# 📜 Deep-dive: Write-Ahead Logging & Database Storage Engine

Thư mục chứa các tài liệu phân tích chuyên sâu từ First Principles về cơ chế vận hành nội tại của Database Storage Engine (PostgreSQL), quản lý bộ nhớ Shared Buffers, Write-Ahead Logging (WAL), Checkpointing, và các kỹ thuật tối ưu hóa dung lượng/I/O trong hệ thống xử lý dữ liệu lớn.

---

## 📑 Danh mục tài liệu

1. [`01-wal-and-storage-engine-internals.md`](./01-wal-and-storage-engine-internals.md):
   - **D0 — Vấn đề**: Nan đề Hiệu năng vs Độ bền vững (Random Disk I/O vs Crash Durability).
   - **D1 — Từ vựng**: Shared Buffers, Data Page 8KB, Dirty Page, WAL, LSN, Checkpointer.
   - **D2 — Cơ chế**: Luồng runtime từ INSERT $\to$ RAM $\to$ WAL $\to$ Commit $\to$ Checkpointing (Write Coalescing).
   - **D3 — Phục hồi & Rủi ro**: Cơ chế Crash Recovery (REDO log), hiện tượng phình to WAL khi chạy 1M transaction.
   - **D4 — Giải pháp kiến trúc**: Bounded Chunking (25.000 records) trong `scan-service` (`BT-09B`) và bảng cấu hình PostgreSQL High-Throughput.
