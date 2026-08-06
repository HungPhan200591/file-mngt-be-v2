# SC-01 — Scan một triệu filesystem entry

## Scenario

Làm sao duyệt một hoặc nhiều `rootKey` có tổng một triệu file/folder với memory bị chặn, progress/checkpoint, batch persistence và backpressure, nhưng không biến preview thành một HTTP response khổng lồ?

## Prerequisite và trạng thái

- Prerequisite: UC-01 đúng ở fixture nhỏ.
- Trạng thái study: Đang nghiên cứu; chưa có benchmark chứng minh V2 đạt quy mô này.
- Không thay architecture, contract, owner context hoặc `docs/STATUS.md`.

## Study pack

| Artifact | Vị trí | Trạng thái |
| --- | --- | --- |
| Deep-dive | [01-deep-dive.md](./01-deep-dive.md) | Đã tạo |
| Summary | `summary/` | Chưa yêu cầu |
| Question bank | `question-bank/` | Chưa yêu cầu |
| Evidence benchmark/failure drill | Link từ đây khi có | Chưa tạo |

Summary chỉ cô đọng từ deep-dive; question bank chỉ sinh từ nội dung đã kiểm chứng. Không tạo folder rỗng hoặc placeholder cho hai artifact này.

## Evidence cần có trước khi chốt

- Peak heap, entry/s, thời gian hoàn tất, số proposal/issue và số lần resume.
- Proof không đọc/ghi ngoài root, không duplicate khi retry chunk và backpressure bảo vệ disk/DB.
- So sánh ít nhất hai batch/concurrency setting trên workload đã chốt; không gọi một setting là tối ưu khi chưa benchmark.

Xem [Scale & Capacity Track](../README.md) để biết workload contract chung và [UC-01](../../core-flows/uc-01-scan-to-catalog-canonical-ingestion/README.md) cho flow nhỏ làm prerequisite.
