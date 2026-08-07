# 022 Durable scan run lease (BT-01) — Design

Owner: `scan-service`
Brief: [01-brief.md](./01-brief.md)

## High Level Design

[Skill: mermaid-styling]

```mermaid
flowchart TB
    Client["<font color='white'>HTTP Client / Admin</font>"] -->|"1: POST /api/v2/scans/previews"| Service["<font color='white'>ScanService<br/>validate root access</font>"]
    Service -->|"Root missing / unreadable"| RootUnavailable["<font color='white'>503 ProblemDetail<br/>không tạo scan run</font>"]
    Service -->|"Root available"| LeaseCheck{"<font color='white'>Active lease?</font>"}
    LeaseCheck -->|"Yes (active lease)"| Conflict["<font color='white'>Throw ScanRunAlreadyRunningException</font>"]
    LeaseCheck -->|"No (free or expired)"| CreateRun["<font color='white'>Create ScanRun (status=RUNNING, workerId, leaseUntil)</font>"]
    CreateRun -->|"3: Dispatch async task"| Executor["<font color='white'>ScanExecutor</font>"]
    
    subgraph Execution["<font color='white'>Async Chunk Execution</font>"]
        Executor -->|"4: Files.walk & analyze"| ChunkCommitter["<font color='white'>ScanChunkCommitter<br/>REQUIRES_NEW</font>"]
        ChunkCommitter -->|"5: Verify workerId & lease"| LeaseValid{"<font color='white'>Worker holds lease?</font>"}
        LeaseValid -->|"No"| Abort["<font color='white'>Throw ScanLeaseExpiredException & Abort</font>"]
        LeaseValid -->|"Yes"| SaveChunk["<font color='white'>Save Proposals + Issues & Extend leaseUntil & Update checkpoint</font>"]
    end
    
    SaveChunk -->|"6: Finalize run"| Complete["<font color='white'>Complete ScanRun (status=COMPLETED)</font>"]

    style Client fill:#2196F3,stroke:#fff,stroke-width:2px
    style Service fill:#4CAF50,stroke:#fff,stroke-width:2px
    style RootUnavailable fill:#E91E63,stroke:#fff,stroke-width:2px
    style LeaseCheck fill:#FF9800,stroke:#fff,stroke-width:2px
    style Conflict fill:#E91E63,stroke:#fff,stroke-width:2px
    style CreateRun fill:#4CAF50,stroke:#fff,stroke-width:2px
    style Executor fill:#9C27B0,stroke:#fff,stroke-width:2px
    style ChunkCommitter fill:#009688,stroke:#fff,stroke-width:2px
    style LeaseValid fill:#FF9800,stroke:#fff,stroke-width:2px
    style Abort fill:#E91E63,stroke:#fff,stroke-width:2px
    style SaveChunk fill:#4CAF50,stroke:#fff,stroke-width:2px
    style Complete fill:#4CAF50,stroke:#fff,stroke-width:2px
```

## Quyết định

1. **Cấu trúc Lease và Checkpoint trên `scan_run`**:
   - `worker_id` (varchar 100): Tên định danh tiến trình worker đang giữ lease (ví dụ `worker-hostname` hoặc UUID ngẫu nhiên cho mỗi tiến trình).
   - `lease_until` (timestamptz): Thời điểm hết hạn lease. Mặc định lease gia hạn thêm 60 giây sau mỗi chunk commit.
   - `checkpoint_chunk` (integer): Số thứ tự chunk đã commit thành công vào DB.
   - `checkpoint_at` (timestamptz): Thời điểm commit chunk gần nhất.
   - Counters: `scanned_file_count`, `proposal_count`, `issue_count` được cập nhật liên tục qua từng chunk thay vì chỉ cập nhật 1 lần lúc `complete`.

2. **Cơ chế Claim Root và thu hồi Stale Lease**:
   - Khi nhận request `start(rootKey)`:
   - Tìm các run có `rootKey` và `status = RUNNING`.
   - Nếu tìm thấy run mà `leaseUntil > Instant.now()`: Báo lỗi `ScanRunAlreadyRunningException` (409 Conflict).
   - Nếu `leaseUntil <= Instant.now()` hoặc NULL (dự phòng legacy run): Đánh dấu run cũ là `FAILED` với lý do `Lease expired`, flush DB để giải phóng partial unique constraint `ux_scan_run_running_root`, sau đó tạo run mới với lease mới.

3. **Transaction Isolation cho Chunk Commit**:
   - Tách logic commit chunk thành `@Component ScanChunkCommitter` với phương thức `@Transactional(propagation = Propagation.REQUIRES_NEW)`.
   - Giúp mỗi chunk 500 items được commit ngay lập tức xuống DB. Nếu ứng dụng sập sau chunk N, dữ liệu của chunk 1..N vẫn nằm bền vững trong DB.
   - Trong phương thức commit chunk:
     1. Query lại `ScanRunEntity` theo `runId`.
     2. Kiểm tra `status == RUNNING` và `workerId.equals(currentWorkerId)`. Nếu không thỏa mãn, ném `ScanLeaseExpiredException` để dừng loop walker.
     3. Save proposal buffer và issue buffer.
     4. Tăng `checkpointChunk`, cộng dồn counters, cập nhật `checkpointAt = Instant.now()`, gia hạn `leaseUntil = Instant.now().plus(leaseDuration)`.

## Domain và data ownership

- Bảng `scan_run` thuộc sở hữu độc quyền của `scan-service` (`scan_db`).
- Schema update qua Flyway migration `V7__add_scan_run_durable_lease.sql`.

## REST/event contract

- Bugfix follow-up: `POST /api/v2/scans/previews` trả `503` với type `urn:filemngt:problem:scan-root-unavailable` nếu root đã cấu hình nhưng path không tồn tại/không đọc được. Request này không tạo `scan_run` và không dispatch worker.
- Response `202 ScanRun` và Kafka event không thay đổi.
- Bổ sung cấu hình `scan.lease-duration-seconds` (mặc định: 60) trong `ScanProperties`.

## Luồng lỗi, idempotency và consistency

- **Mất lease giữa chừng**: Worker chạy chậm vượt quá `leaseUntil` khiến worker khác chiếm lease hoặc stale cleanup can thiệp. Khi worker cũ chuẩn bị commit chunk tiếp theo, `ScanChunkCommitter` phát hiện `workerId` không khớp hoặc status không phải `RUNNING` -> ném exception ngắt tiến trình worker cũ ngay lập tức.
- **Service restart**: Khi service restart, `cleanupOrphanRunningScans` kiểm tra các run `RUNNING` có lease hết hạn để đánh dấu `FAILED`.

## Hiệu năng, quan sát và bảo mật tối thiểu

- Memory footprint cố định ở mức `BATCH_SIZE = 500` items nhờ flush chunk độc lập.
- Round-trip DB được tối ưu: mỗi chunk 500 items mới thực hiện 1 transaction flush proposal, issue và update status run.
