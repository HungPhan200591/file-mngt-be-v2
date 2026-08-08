# FT-028 — Kết quả benchmark reconciliation

Ngày tổng hợp: 2026-08-08

Các phép đo dưới đây chạy trên PostgreSQL Testcontainers, workload 1.000.000 diff row. Thời gian `write` chỉ đo phần persistence được nêu; thời gian seed không được tính vào `transactionMs` trừ khi ghi chú riêng.

## 1. Baseline production-like scan

Nguồn: run scan thực tế trước tối ưu.

| Pha | Thời gian |
|---|---:|
| Tổng 1M cold changed | 84.651s |
| Discovery + staging COPY | 5.563s |
| Materialize diff | 2.888s |
| Analyze | 6.919s |
| Persistence-side suy ra | ~64.508s |
| Finalize | 1.971s |

Filesystem-only được đo riêng khoảng **17.832s/1M**.

## 2. JDBC batch 50.000 row

Workload: 1M inventory, 900k proposal, 100k issue; `Invalid*` chuyển sang issue; mỗi batch một transaction.

### 2.1 JDBC batch ban đầu

| Chỉ số | Kết quả |
|---|---:|
| Tổng write | 44.557s |
| Read mỗi batch | 32–41ms |
| Inventory mỗi batch | ~0.7–0.8s |
| Proposal mỗi batch | ~1.1–1.3s |
| Transaction mỗi batch | ~1.8–2.1s |

### 2.2 Bật `reWriteBatchedInserts=true`

| Chỉ số | Kết quả |
|---|---:|
| Tổng write | 43.454s |
| Cải thiện | 1.103s (~2,5%) |

Kết luận: JDBC round-trip không phải bottleneck chính; chi phí row/index/WAL mới chi phối.

## 3. Set-based SQL

Workload giống JDBC benchmark nhưng dùng ba câu `INSERT ... SELECT` trực tiếp từ `scan_inventory_diff_stage`.

### 3.1 Kết quả set-based

| Bước | Thời gian |
|---|---:|
| Seed diff stage | 2.523–2.539s |
| Inventory 1M | 5.264–5.436s |
| Proposal 900k | 12.006–12.209s |
| Issue 100k | 1.401–1.697s |
| Tổng transaction | 18.674–19.348s |
| Tổng gồm seed | ~21.213s |

So với JDBC batch `43.454s`, set-based giảm khoảng **24.1–24.8s (~55–57%)**.

Lưu ý: benchmark này dùng rule đơn giản và evidence `{}`; chưa đại diện đầy đủ parser/evidence của scan production.

## 4. Phân rã invariant của proposal

Kết quả chạy trên PostgreSQL 18 với cùng 900k proposal:

| Biến thể | Thời gian | Chênh lệch so baseline |
|---|---:|---:|
| Đầy đủ FK + unique | 12.210s | — |
| Bỏ FK, giữ unique | 4.745s | -7.465s (~61%) |
| Giữ FK, bỏ unique | 11.644s | -0.566s (~4,6%) |
| UUIDv7 native + FK + unique | 11.215s | -0.995s (~8,1%) |

Diễn giải:

- FK là chi phí lớn nhất trong phép đo proposal.
- Unique constraint có chi phí thấp hơn nhưng vẫn bảo vệ idempotency.
- UUIDv7 cải thiện locality của primary-key index nhưng không thay thế được tối ưu FK.

## 5. Bảng so sánh tổng hợp

| Phương án | Phạm vi | Thời gian |
|---|---|---:|
| JDBC batch | Persistence 1M diff | 44.557s |
| JDBC batch + rewrite | Persistence 1M diff | 43.454s |
| Set-based | Persistence gần 2M row | 18.674–19.348s |
| Set-based + seed | Seed + persistence | ~21.213s |
| Production-like scan cũ | Full scan | 84.651s |

## 6. Kết luận hiện tại

Set-based đã chứng minh persistence không còn là bottleneck 64s nếu dữ liệu đã được chuẩn hóa trong database. Tuy nhiên không được thay thế parser production bằng rule SQL đơn giản: parser/evidence vẫn chạy trong Java, sau đó `COPY` trực tiếp proposal/issue vào bảng source of truth; inventory đi bằng set-based SQL từ `scan_inventory_diff_stage`.

Mục tiêu tổng scan dưới 30s vẫn phải đo lại sau khi tối ưu đồng thời filesystem, materialize/analyze/finalize, lease và transaction boundary.
