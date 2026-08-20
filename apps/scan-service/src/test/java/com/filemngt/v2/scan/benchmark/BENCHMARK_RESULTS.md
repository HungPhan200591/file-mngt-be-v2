# 📊 Scan Service Benchmark Results Dashboard

Tài liệu này là **Source of Truth tổng hợp (Executive Dashboard)** toàn bộ kết quả đo đạc hiệu năng của `scan-service` theo từng bước trong pipeline xử lý 1.000.000 files (**SC-01**).

Mọi số liệu trong dashboard đều có bằng chứng thực nghiệm đối chứng giữa **Trước tối ưu (Legacy / Baseline)** và **Sau tối ưu (Optimized / Candidate)** kèm link dẫn trực tiếp tới mã nguồn bài test và báo cáo phân tích chi tiết.

---

## 1. Bảng Tổng hợp Toàn cảnh theo từng Bước Pipeline (Master Matrix)

```text
[BƯỚC 1: Scan Discovery & Ingestion] ➔ [BƯỚC 2: Reconciliation Diff] ➔ [BƯỚC 3: Approval Sharding] ➔ [BƯỚC 4: Outbox Continuous Drain]
```

| Bước trong Pipeline | Mã bài đo | Trước khi tối ưu (Legacy Baseline) | Sau khi tối ưu (Optimized / Candidate) | Mức cải thiện (Delta / Speedup) | Báo cáo chi tiết & Test Class |
| :--- | :--- | :--- | :--- | :---: | :--- |
| **BƯỚC 1**<br>Scan Discovery & Ingestion (1M Files) | **`BENCH-01-SCAN-CORE`** | **84,65s (~23.000 files/s)**<br>*(JDBC Batching 50k)* | ⚡ **Cold: 24,85s (~40.240 files/s)**<br>⚡ **Warm: 8,11s (~123.000 files/s)**<br>*(Direct COPY + Chunking 25k)* | 🚀 **Nhanh hơn 3,4x (Cold)**<br>🚀 **Nhanh hơn 10,4x (Warm)** | 📜 [Báo cáo Scan Core](file:///d:/Personal/file-management/v2/file-mngt-be-v2/apps/scan-service/src/test/java/com/filemngt/v2/scan/benchmark/results/03-scan-core-pipeline-benchmark.md)<br>🧪 [`ScanCorePipelineBenchmarkTest.java`](file:///d:/Personal/file-management/v2/file-mngt-be-v2/apps/scan-service/src/test/java/com/filemngt/v2/scan/benchmark/preview/ScanCorePipelineBenchmarkTest.java) |
| **BƯỚC 2**<br>Inventory Diff & Reconciliation (1M Diff) | **`BENCH-02-DIFF-QUERY`** | **278ms – 3.903ms**<br>*(Correlated Subquery `NOT EXISTS`)* | ⚡ **70ms – 488ms**<br>*(Optimized `LEFT JOIN ... IS NULL`)* | 🚀 **Nhanh hơn từ 4,0x đến 25,0x** tùy kịch bản | 📜 [Báo cáo Diff Query](file:///d:/Personal/file-management/v2/file-mngt-be-v2/apps/scan-service/src/test/java/com/filemngt/v2/scan/benchmark/results/04-inventory-diff-query-benchmark.md)<br>🧪 [`InventoryDiffQueryBenchmarkTest.java`](file:///d:/Personal/file-management/v2/file-mngt-be-v2/apps/scan-service/src/test/java/com/filemngt/v2/scan/benchmark/preview/InventoryDiffQueryBenchmarkTest.java) |
| **BƯỚC 3**<br>Approval Decision & Outbox (1M Proposals) | **`BENCH-03-APPROVAL`** | ❌ **CRASH tại 1M** (PSQLException > 65.535 params)<br>*(JPA `findAllById`)*<br>*(FT-045 1 Shard: 148,79s ~ 6.721/s)* | ⚡ **30,76s (~32.511 records/s)**<br>*(FT-051 Logical Sharding: 4 Shards song song + Binary COPY)* | 🚀 **Vượt qua Crash 1M**<br>🚀 **Nhanh hơn 4,8x** so với Single Writer | 📜 [Báo cáo Approval 05](file:///d:/Personal/file-management/v2/file-mngt-be-v2/apps/scan-service/src/test/java/com/filemngt/v2/scan/benchmark/results/05-legacy-approval-decision-batch-baseline.md)<br>📜 [FT-051 Brief](file:///d:/Personal/file-management/v2/file-mngt-be-v2/docs/features/051-logical-approval-sharding/01-brief.md)<br>🧪 [`ApprovalDecisionChunkingBenchmarkTest.java`](file:///d:/Personal/file-management/v2/file-mngt-be-v2/apps/scan-service/src/test/java/com/filemngt/v2/scan/benchmark/approval/ApprovalDecisionChunkingBenchmarkTest.java) |
| **BƯỚC 4**<br>Outbox Drain & Relay to Kafka | **`BENCH-04-OUTBOX-DRAIN`** | **25k: 6,58s (3.800 events/s)**<br>❌ **1M: Bị treo ~6 phút** (`aborted`)<br>*(Wave Barrier + 50ms Fixed Delay)* | ⚡ **25k: 522ms (47.893 events/s)**<br>⚡ **1M: 8,264s (121.007 events/s)**<br>*(FT-053: 64 lane logic, 4 worker, native JDBC)* | 🚀 **25k nhanh hơn 8,9x so với FT-052**<br>🚀 **1M vượt isolated floor 30k/s** | 📜 [Báo cáo FT-052](file:///d:/Personal/file-management/v2/file-mngt-be-v2/apps/scan-service/src/test/java/com/filemngt/v2/scan/benchmark/results/06-ft052-legacy-outbox-wave-baseline.md)<br>📜 [Báo cáo FT-053](file:///d:/Personal/file-management/v2/file-mngt-be-v2/apps/scan-service/src/test/java/com/filemngt/v2/scan/benchmark/results/07-ft053-lane-fenced-outbox-relay.md)<br>🧪 [`ScanOutboxLaneRelayBenchmarkTest.java`](file:///d:/Personal/file-management/v2/file-mngt-be-v2/apps/scan-service/src/test/java/com/filemngt/v2/scan/benchmark/outbox/ScanOutboxLaneRelayBenchmarkTest.java) |

---

## 2. Chi tiết Đo đạc & Phân rã Kỹ thuật theo từng Bước

### 🔹 BƯỚC 1: Scan Discovery & Ingestion Pipeline (1.000.000 Files)
* **Mã bài đo**: `BENCH-01-SCAN-CORE` (Gồm các bước phụ: `BENCH-01A Legacy JDBC`, `BENCH-01B Set-based COPY`, `BENCH-01C Pipeline 25k`).
* **Mục tiêu**: Đọc toàn bộ cây thư mục 1M file, bóc tách regex/metadata, so khớp thay đổi và ghi nhận vào cơ sở dữ liệu.
* **Chi tiết từng khâu khi chạy Cold Pipeline (< 25s, Tổng cộng ≈ 24.850 ms trên Desktop PC)**:

| Khâu thực thi | Chi tiết tác vụ kỹ thuật | Thời gian (ms) | % Thời gian | Tốc độ tương đương |
| :--- | :--- | :---: | :---: | :---: |
| **1. Phân tích Regex & Keyset** | 8 Virtual Threads parse 1M files + streaming | **~7.200 ms** | **29,0%** | ~138.800 files/s |
| **2. Ghi Proposals vào DB** | PostgreSQL Binary `COPY scan_proposal` (990.000 dòng) | **~6.800 ms** | **27,4%** | ~145.500 proposals/s |
| **3. Ghi Inventory vào DB** | Direct SQL `INSERT INTO scan_file_inventory` (1M dòng) | **~5.100 ms** | **20,5%** | ~196.000 rows/s |
| **4. Discovery Stream Staging** | In-Memory Stream $\to$ COPY `scan_inventory_stage` | **~4.500 ms** | **18,1%** | ~222.000 files/s |
| **5. Finalize, Issue & Checkpoint**| Ghi 10k Issues, commit chunk 25k, dọn staging | **~1.250 ms** | **5,0%** | — |
| **TỔNG CỘNG (COLD RUN)** | **Xử lý toàn diện 1.000.000 Files (Desktop PC)** | **~24.850 ms** | **100%** | **~40.240 files/s** |
| *(Tham chiếu WARM UNCHANGED)* | *1M files không đổi (Bỏ qua ghi proposals/inventory)* | *~8.112 ms* | *—* | *> 123.000 files/s* |

---

### 🔹 BƯỚC 2: Inventory Diff Query & Reconciliation
* **Mã bài đo**: `BENCH-02-DIFF-QUERY`
* **Mục tiêu**: So khớp 1M dòng staged với 1M dòng inventory hiện có để tìm ra các file thêm mới, sửa đổi hoặc xóa bỏ.

| Kịch bản kiểm thử (Scenario) | Legacy Correlated Subquery | Optimized `LEFT JOIN ... IS NULL` | Tỷ lệ tăng tốc (Speedup) |
| :--- | :---: | :---: | :---: |
| `COLD` (Toàn bộ file mới) | 278 ms | **70 ms** | **4,0x** |
| `UNCHANGED` (Không có thay đổi) | 3.171 ms | **482 ms** | **6,6x** |
| `INCREMENTAL` (Thay đổi 1 phần) | 3.261 ms | **381 ms** | **8,6x** |
| `FULL_CHANGE` (Thay đổi toàn bộ) | 3.903 ms | **488 ms** | **8,0x** |
| `REVIVED` (File cũ xuất hiện lại) | 3.775 ms | **151 ms** | **25,0x** |

---

### 🔹 BƯỚC 3: Approval Decision & Outbox Persistence (1.000.000 Proposals)
* **Mã bài đo**: `BENCH-03-APPROVAL`
* **Mục tiêu**: Chuyển trạng thái 1M proposal sang `APPROVED`, đồng thời ghi 1M bản ghi `scan_outbox_event` nguyên tử trong cùng transaction.

| Thế hệ kiến trúc | Cơ chế thực thi | Workload 25k | Workload 1M | Throughput 1M | Trạng thái & Ghi chú |
| :--- | :--- | :---: | :---: | :---: | :--- |
| **Thế hệ 1 (Legacy JPA)** | `decideAll()` nạp toàn bộ proposal ID qua Hibernate | 4.139 ms | ❌ **CRASH** | — | Giới hạn 65.535 bind parameters của PostgreSQL JDBC |
| **Thế hệ 2 (FT-045 Chunking)** | Single Writer chia bounded chunk 25k (`REQUIRES_NEW`) | 5.648 ms | **148.794 ms** | 6.721 records/s | Vượt qua lỗi crash, hoàn tất 40 chunks trong ~2m28s |
| **Thế hệ 3 (FT-051 Sharding)** | **4 Logical Shards song song** + Hash routing + COPY | — | **30.759 ms** | 🚀 **32.511 records/s** | **Nhanh hơn 4,8x** so với single writer (Runtime Default) |

---

### 🔹 BƯỚC 4: Outbox Continuous Drain & Relay to Kafka
* **Mã bài đo**: `BENCH-04-OUTBOX-DRAIN`
* **Mục tiêu**: Claim các outbox event chưa gửi, dispatch bất đồng bộ sang Kafka broker và đánh dấu `published_at`.

| Thế hệ kiến trúc | Cơ chế thực thi | Workload 25k | Workload 1M | Throughput (25k) | Trạng thái & Đánh giá |
| :--- | :--- | :---: | :---: | :---: | :--- |
| **Thế hệ 1 (Legacy Wave)** | `SKIP LOCKED` theo đợt (batch 500) + Fixed Delay 50ms | 6.579 ms | ❌ **Treo ~6 min** | 3.800 events/s | Lãng phí 2.450 ms khoảng chết (49 wave × 50ms) |
| **Thế hệ 2 (FT-052 Continuous Drain)**| **Sliding Window 500** + Continuous Refill + Pressure Gate | **4.641 ms** | ❌ **Treo / Aborted** | ⚡ **5.387 events/s** | **Throughput tăng +41,8%** (25k); 1M bị nghẽn do JPA entity, per-event lease và count polling |
| **Thế hệ 3 (FT-053 Lane Relay)** | **64 virtual lane + 4 worker + native JDBC fetch/fenced mark** | **522 ms** | **8.264 ms** | ⚡ **121.007 events/s** | **PASS isolated immediate-ack floor**; chưa là real Kafka, representative payload hoặc production evidence |

**Qualification gate còn mở:** FT-053 đã đạt isolated immediate-ack 1M nhưng còn thiếu real Kafka,
representative payload, repeatability, crash/reclaim, broker failure và overlapped approval evidence. Xem
[FT-053 Plan](file:///d:/Personal/file-management/v2/file-mngt-be-v2/docs/features/053-lane-fenced-outbox-data-plane/03-plan.md).

---

### 3. Tiến hóa Hiệu năng Tổng thể (Visual ASCII Charts)

```text
[BƯỚC 1: Scan Core 1M Files]
Thế hệ 1 (JDBC Batch)        ████████████████████████████████████████ 84.65s (Throughput: 23k/s)
Thế hệ 2 (Set-based + COPY)  ██████████ 24.90s                               (Throughput: 40k/s)
Thế hệ 3 (Pipelined 25k PC)  ██████████ 24.85s                               (Cold < 25s / Warm 8.11s)

[BƯỚC 3: Approval 1M Proposals]
Thế hệ 1 (Legacy JPA)        ❌ CRASH (Vượt ngưỡng 65.535 SQL parameters)
Thế hệ 2 (FT-045 Chunking)   ████████████████████████████████████████ 148.79s (Throughput: 6.721/s)
Thế hệ 3 (FT-051 4 Shards)   ████████ 30.76s                                 (Throughput: 32.511/s, +383%)

[BƯỚC 4: Outbox Relay 25k Events]
Thế hệ 1 (Wave + 50ms Delay) ████████████████ 6.58s                          (Throughput: 3.800/s; 1M aborted)
Thế hệ 2 (FT-052 Continuous) ███████████ 4.64s                               (Throughput: 5.387/s, +41.8%)
Thế hệ 3 (FT-053 Lane Relay) █ 0.52s                                         (Throughput: 47.893/s, immediate ack)
```

---

## 4. Chỉ mục Toàn bộ Báo cáo Đo đạc Chi tiết (Benchmark Detail Index)

1. 📜 **[01-legacy-jdbc-batch-baseline.md](file:///d:/Personal/file-management/v2/file-mngt-be-v2/apps/scan-service/src/test/java/com/filemngt/v2/scan/benchmark/results/01-legacy-jdbc-batch-baseline.md)**: Phân tích nguyên nhân điểm nghẽn 64.5s ở tầng JDBC batching.
2. 💾 **[02-database-set-based-persistence.md](file:///d:/Personal/file-management/v2/file-mngt-be-v2/apps/scan-service/src/test/java/com/filemngt/v2/scan/benchmark/results/02-database-set-based-persistence.md)**: Phân rã chi phí ForeignKey, Unique Constraint, UUIDv7 và SQL Set-based.
3. 🚀 **[03-scan-core-pipeline-benchmark.md](file:///d:/Personal/file-management/v2/file-mngt-be-v2/apps/scan-service/src/test/java/com/filemngt/v2/scan/benchmark/results/03-scan-core-pipeline-benchmark.md)**: Báo cáo phân tích chi tiết từng khâu Core Pipeline 1.000.000 files (< 25s Cold / 8.1s Warm).
4. 🔍 **[04-inventory-diff-query-benchmark.md](file:///d:/Personal/file-management/v2/file-mngt-be-v2/apps/scan-service/src/test/java/com/filemngt/v2/scan/benchmark/results/04-inventory-diff-query-benchmark.md)**: Đối chiếu hiệu năng Correlated Subquery vs `LEFT JOIN ... IS NULL` trên 1M inventory diff (nhanh hơn từ 4x đến 25x).
5. 🧱 **[05-legacy-approval-decision-batch-baseline.md](file:///d:/Personal/file-management/v2/file-mngt-be-v2/apps/scan-service/src/test/java/com/filemngt/v2/scan/benchmark/results/05-legacy-approval-decision-batch-baseline.md)**: So sánh Legacy JPA vs FT-045 Bounded Chunking (25k trong 4.139s; 1M trong 148.794s; legacy 1M crash do bind parameters).
6. 📤 **[06-ft052-legacy-outbox-wave-baseline.md](file:///d:/Personal/file-management/v2/file-mngt-be-v2/apps/scan-service/src/test/java/com/filemngt/v2/scan/benchmark/results/06-ft052-legacy-outbox-wave-baseline.md)**: So sánh Legacy Outbox Wave Baseline (25k: 6.579s ~ 3.800 rec/s; 1M aborted) vs Candidate FT-052 Continuous Drain (25k: 4.641s ~ 5.387 rec/s).
7. 📤 **[07-ft053-lane-fenced-outbox-relay.md](file:///d:/Personal/file-management/v2/file-mngt-be-v2/apps/scan-service/src/test/java/com/filemngt/v2/scan/benchmark/results/07-ft053-lane-fenced-outbox-relay.md)**: FT-053 native lane relay (25k: 522ms ~ 47.893 rec/s; 1M: 8.264s ~ 121.007 rec/s, immediate-ack only).
8. 🚀 **[`ScanApprovalPipelineBenchmarkTest.java`](file:///d:/Personal/file-management/v2/file-mngt-be-v2/apps/scan-service/src/test/java/com/filemngt/v2/scan/benchmark/approval/ScanApprovalPipelineBenchmarkTest.java)**: Benchmark trọn gói End-to-End Approval Pipeline (Duyệt proposals 4 shards song song + Outbox Relay 64 lanes bắn gối đầu sang Kafka) cho cả 25k và 1M workload.
