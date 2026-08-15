# SC-01 — Khung mục tiêu hiệu năng & Chỉ số SLO (Chính thức)

Ngày chốt: 2026-08-15  
Phạm vi: Chỉ tiêu SLO chính thức của SC-01 (Quy mô 1.000.000 files/records).  
*Tài liệu nghiên cứu sâu về giới hạn vật lý phần cứng: xem [Deep-dive Hardware & Physical Limits](../../../deep-dive/media-asset-management-architecture/02-performance-slo-and-hardware-benchmarks.md).*

---

## 1. Bảng chỉ tiêu SLI / SLO chính thức (Workload 1.000.000 Records)

| Mã SLI | Nghiệp vụ thực thi | Target SLO (P90) | Ngưỡng chặn (P99) | Throughput toàn tuyến | Trạng thái hiện tại |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **`SLI-01`** | **Cold Scan 1M Files** (Walk $\rightarrow$ Staging $\rightarrow$ Parse $\rightarrow$ Proposal DB) | **$\le$ 30.0s** | **$\le$ 45.0s** | $\ge 33.000$ files/s | **`ĐẠT`** (Thực tế: **25.76s**) |
| **`SLI-02`** | **Warm Scan 1M Files** (Periodic Re-scan, 0 changed files) | **$\le$ 8.0s** | **$\le$ 12.0s** | $\ge 125.000$ files/s | **`ĐẠT`** (~5.5s - 7.0s) |
| **`SLI-03`** | **Approve 1M Records** (Scan Outbox $\rightarrow$ Catalog Coalesce $\rightarrow$ Query DB Ready) | **$\le$ 30.0s** | **$\le$ 45.0s** | $\ge 33.000$ records/s | **`MỤC TIÊU TỐI ƯU`** |
| **`SLI-04`** | **Search Ready 1M Records** (Elasticsearch Bulk Async Lane) | **$\le$ 60.0s** | **$\le$ 90.0s** | $\ge 15.000$ docs/s | **`ASYNC LANE`** |

---

## 2. Phân rã ngân sách thời gian (Latency Budget)

### 2.1. Cold Scan 1M Files (Tổng ngân sách: 30.0s)
- Filesystem Discovery (`walkFileTree` streaming): **5.0s**
- PostgreSQL Staging Write (`COPY FROM STDIN` Unlogged): **3.0s**
- Set-based SQL Diff (`INSERT SELECT` + Anti-join): **2.5s**
- Semantic Parsing (8 Partition Virtual Threads Regex): **6.5s**
- Fast-path Inventory Insert + Proposals/Issues `COPY`: **11.5s**
- Checkpoint, Lease Fencing & Finalize SSE: **1.5s**

### 2.2. Approve 1M Records $\rightarrow$ `QUERY_DB_READY` (Tổng ngân sách: 30.0s)
- Scan DB Approve + Outbox `COPY` 1M: **4.0 – 6.0s**
- Scan Outbox Continuous Drain $\rightarrow$ 12 Kafka Partitions: **3.0 – 5.0s**
- Catalog Batch Listener + Coalesce (1M $\rightarrow$ ~150k subjects) + DB `COPY`: **8.0 – 12.0s**
- Catalog Outbox Relay $\rightarrow$ Kafka (150k snapshot events): **1.5 – 2.5s**
- Query Bulk Upsert DB (~150k subjects): **5.0 – 8.0s**
- Redis Generation Key Switch (`cache:gen:2:*`) & Watermark: **< 0.1s**
- Buffer dự phòng biến động I/O: **1.5 – 3.5s**

---

## 3. Tiêu chí Đạt / Không đạt (Pass/Fail Gate)

1. **Cold Scan 1M**: `PASS` khi runtime hoàn tất $\le 30.0\text{s}$, không warning timeout, lease fencing hợp lệ.
2. **Approve 1M**: `PASS` khi `QUERY_DB_READY` đạt được trong vòng $\le 30.0\text{s}$ kể từ lúc bấm Approve All, 0 tin nhắn rơi vào DLT.
