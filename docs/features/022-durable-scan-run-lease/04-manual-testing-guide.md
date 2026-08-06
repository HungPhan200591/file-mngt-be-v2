# 022 Durable scan run lease (BT-01) — Kế hoạch & Hướng dẫn Test Thủ công

Owner: `scan-service`  
Feature: [022 Durable scan run lease](./03-plan.md)  
Break Task: [BT-01](../../manual/learning/use-cases/scale-capacity/sc-01-scan-one-million-filesystem-entry/04-break-task.md#bt-01--durable-scan-run)

---

## 1. Chuẩn bị Môi trường

1. **Database:** PostgreSQL container/local đang chạy trên port `15432` (`scan_db`).
2. **Catalog Service:** `catalog-service` khởi động trên port `18101`.
3. **Scan Service:** `scan-service` khởi động trên port `18102`.
4. **Flyway Verification:** Đảm bảo `scan-service` đã áp dụng Flyway migration `V7__add_scan_run_durable_lease.sql` (bổ sung 4 cột `worker_id`, `lease_until`, `checkpoint_chunk`, `checkpoint_at` vào bảng `scan_run`).

---

## 2. Kịch bản Test 1: Khởi tạo Scan & Kiểm tra Durable Lease & Checkpoint

### Bước 1.1: Gửi Request Khởi tạo Scan Preview
Gửi HTTP `POST` tới `scan-service`:

- **URL:** `http://localhost:18102/api/v2/scans/previews`
- **Method:** `POST`
- **Header:** `Content-Type: application/json`
- **Body:**
  ```json
  {
    "rootKey": "fixture"
  }
  ```

- **Kết quả kỳ vọng (Response 202 Accepted):**
  ```json
  {
    "id": "e4a91b2c-...",
    "rootKey": "fixture",
    "profile": "JOKE_VIDEO",
    "status": "RUNNING",
    "scannedFileCount": 0,
    "proposalCount": 0,
    "issueCount": 0
  }
  ```

### Bước 1.2: Truy vấn Cơ sở Dữ liệu Kiểm tra Lease & Checkpoint
Mở DBeaver / `psql` kết nối vào `scan_db` và chạy SQL:

```sql
SELECT id, root_key, status, worker_id, lease_until, checkpoint_chunk, checkpoint_at, scanned_file_count, proposal_count 
FROM scan_run 
ORDER BY started_at DESC 
LIMIT 1;
```

- **Kết quả kỳ vọng trong DB:**
  - `status`: `RUNNING` (hoặc `COMPLETED` khi quét xong).
  - `worker_id`: Dạng `worker-<uuid>` (ví dụ: `worker-7a8b...`).
  - `lease_until`: Thời gian hết hạn lease (`started_at + 60s`).
  - `checkpoint_chunk`: `>= 1` (ghi nhận chunk đã được commit).
  - `checkpoint_at`: Thời điểm gia hạn lease gần nhất.

---

## 3. Kịch bản Test 2: Khóa Lease — Ngăn Chạy Trùng RootKey

### Bước 2.1: Giả lập Worker Đang Giữ Lease Active
Khi đợt scan ở Bước 1 đang `RUNNING` (hoặc giữ 1 record `scan_run` trong DB có `status = 'RUNNING'` và `lease_until = NOW() + INTERVAL '5 minute'`):

### Bước 2.2: Gửi Request Scan Thứ hai trên cùng RootKey
Gửi lại HTTP `POST`:
- **URL:** `http://localhost:18102/api/v2/scans/previews`
- **Body:**
  ```json
  {
    "rootKey": "fixture"
  }
  ```

- **Kết quả kỳ vọng (Response 409 Conflict):**
  ```json
  {
    "type": "about:blank",
    "title": "Conflict",
    "status": 409,
    "detail": "Scan already running: fixture"
  }
  ```

---

## 4. Kịch bản Test 3: Tự động Thu hồi Stale Lease (Lease Hết Hạn)

### Bước 3.1: Giả lập Run bị Treo / Quá Hạn Lease
Tạo hoặc sửa 1 dòng trong bảng `scan_run` để đóng vai đợt scan bị sập/treo:
```sql
UPDATE scan_run 
SET status = 'RUNNING', 
    lease_until = NOW() - INTERVAL '1 minute' 
WHERE root_key = 'fixture' AND status = 'RUNNING';
```

### Bước 3.2: Gửi Request Scan Mới
Gửi lại HTTP `POST /api/v2/scans/previews` với `rootKey: "fixture"`.

- **Kết quả kỳ vọng:**
  1. Response trả về **202 Accepted** với một `runId` mới.
  2. Run cũ bị đẩy thành `status = 'FAILED'`, `last_error = 'Stale scan run timed out or lease expired'`.
  3. Run mới chiếm lease thành công với `worker_id` mới và `lease_until` mới.

---

## 5. Cách Xem Log Kiểm tra Trên Console Service

Quan sát console log của `scan-service`:

1. **Khi khởi tạo scan mới & gán lease:**
   ```text
   INFO  c.f.v.s.a.scan.ScanService - Khởi tạo đợt scan thành công: runId=e4a9..., rootKey=fixture, workerId=worker-7a8b..., leaseUntil=2026-08-06T15:00:00Z
   ```

2. **Khi commit chunk độc lập (`REQUIRES_NEW`) & gia hạn lease:**
   ```text
   DEBUG c.f.v.s.a.scan.ScanChunkCommitter - Đã commit chunk #1 cho runId=e4a9...: workerId=worker-7a8b..., proposals=1, issues=1, nextLeaseUntil=2026-08-06T15:01:00Z
   ```

3. **Khi từ chối request do Lease Active (Test 2):**
   ```text
   WARN  c.f.v.s.a.scan.ScanService - Không thể mở scan mới do rootKey=fixture đang có run giữ lease active
   ```

4. **Khi thu hồi Stale Lease (Test 3):**
   ```text
   WARN  c.f.v.s.a.scan.ScanService - Phát hiện scan run bị quá hạn lease/timeout: runId=old-uuid, leaseUntil=...
   ```

---

## 6. Check-list Đảm bảo Hoàn thành Feature

| STT | Nội dung kiểm tra | Cách xác minh | Trạng thái |
| --- | --- | --- | --- |
| 1 | Database Schema V7 | Kiểm tra bảng `scan_run` có 4 cột `worker_id`, `lease_until`, `checkpoint_chunk`, `checkpoint_at`. | Đạt |
| 2 | Lease Claim | Tạo scan thành công, `worker_id` dạng `worker-<uuid>`, `lease_until` tăng theo thời gian. | Đạt |
| 3 | Lock Concurrency | Gửi 2 scan cùng `rootKey` khi lease active -> Trả HTTP **409 Conflict**. | Đạt |
| 4 | Stale Lease Expiration | Run quá hạn `lease_until` bị đổi thành `FAILED`, run mới chiếm được root. | Đạt |
| 5 | Chunk Durability | proposals/issues được flush xuống DB theo từng chunk mà không đợi toàn bộ scan hoàn tất. | Đạt |
