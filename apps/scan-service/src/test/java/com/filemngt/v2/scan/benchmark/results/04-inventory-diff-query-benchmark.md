# Benchmark 04 — Inventory Diff Query: Current vs LEFT JOIN

- **Mã bài đo**: `BENCH-04-INVENTORY-DIFF-QUERY`
- **Class thực thi**: [`InventoryDiffQueryBenchmarkTest.java`](../pipeline/InventoryDiffQueryBenchmarkTest.java)
- **Workload**: 1.000.000 rows trong `scan_inventory_stage` và inventory tương ứng
- **Môi trường**: PostgreSQL Testcontainers (`postgres:18.0-alpine`), Java 25, 3 measurements/scenario
- **Phạm vi**: chỉ đo phase SQL diff; không đại diện cho full Scan → Catalog → Query SLO
- **Thời điểm chạy**: 2026-08-16

## Kết quả runtime

| Scenario | Current correlated subquery | Candidate `LEFT JOIN` | Cải thiện xấp xỉ |
| :--- | ---: | ---: | ---: |
| `COLD` | 278 ms | 70 ms | 4,0x |
| `UNCHANGED` | 3.171 ms | 482 ms | 6,6x |
| `INCREMENTAL` | 3.261 ms | 381 ms | 8,6x |
| `FULL_CHANGE` | 3.903 ms | 488 ms | 8,0x |
| `REVIVED` | 3.775 ms | 151 ms | 25,0x |

Các giá trị trên là sample cuối cùng được log trong 3 measurements của mỗi scenario; đây chưa phải median/p95.

## Correctness

Test đối chiếu kết quả count của hai query trong từng scenario trước khi ghi runtime. Không có assertion failure trong log benchmark đã thu thập; khi lưu làm evidence chính thức cần giữ kèm trạng thái IntelliJ `Process finished with exit code 0`.

## Nhận định

- `LEFT JOIN ... IS NULL` nhanh hơn rõ rệt ở cả workload cold và warm.
- Lợi ích lớn nhất xuất hiện khi phải đối chiếu với inventory hiện có (`UNCHANGED`, `INCREMENTAL`, `FULL_CHANGE`, `REVIVED`).
- Kết quả đủ mạnh để đưa candidate vào bước review/thay đổi SQL production có kiểm soát.
- Chưa được coi là bằng chứng end-to-end; cần chạy lại `ScanCorePipelineBenchmarkTest` sau khi candidate được áp dụng để đo tác động lên toàn pipeline.

## Quyết định tiếp theo

`LEFT JOIN` được **đề xuất triển khai ở FT-047**, sau khi xác nhận lần chạy benchmark có exit code 0 và giữ lại baseline này để so sánh trước/sau.
