# 🛠️ Nợ Kỹ Thuật (Technical Debt) & Lộ Trình Refactor Backend V2

Updated: 2026-08-03

Tài liệu này là **Nguồn sự thật duy nhất (SSOT)** quản lý toàn bộ **Nợ kỹ thuật (Technical Debt)**, các đoạn mã nguồn cũ (Legacy Code), Code Smells và Kế hoạch cải tiến (Refactoring Backlog) của hệ thống Backend V2.

---

## 📊 1. Phân Loại Nợ Kỹ Thuật (Debt Taxonomy)

| Cấp độ | Ký hiệu | Tiêu chí Đánh giá | Chiến lược Xử lý |
| :--- | :--- | :--- | :--- |
| **CRITICAL** | 🔴 HIGH RISK | Vi phạm nghiêm trọng ranh giới Service (Boundary Leak), rủi ro sai lệch dữ liệu (Data Inconsistency) hoặc sập hệ thống dưới tải lớn. | Ưu tiên tạo Feature/Plan trả nợ ngay ở Sprint kế tiếp. |
| **MEDIUM** | 🟡 CODE SMELL | Mã nguồn dư thừa do thay đổi kiến trúc (Legacy PoC Code), code trùng lặp hoặc thiếu MDC Correlation Tracing. | Trả nợ kết hợp khi chạm vào module đó trong Feature mới. |
| **LOW** | 🟢 OPTIMIZE | Đặt tên chưa tối ưu, refactor Exception/DTO, nâng cấp dependency minor version. | Xử lý khi có thời gian rảnh hoặc trong các đợt Boundary Cleanup. |

---

## 📋 2. Danh Sách Nợ Kỹ Thuật Hiện Tại (Active Technical Debt Backlog)

| ID | Service | Cấp độ | Tóm tắt Nợ Kỹ thuật | Nguyên nhân / Ngữ cảnh | Kế hoạch Trả nợ (Resolution Plan) | Trạng thái |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **`TD-001`** | `scan-service` | 🔴 CRITICAL | `ScanService` trả về public result type và exception leak ra bên ngoài controller. | Code giai đoạn P1/P2 chưa tách biệt hoàn toàn Application DTO & Controller DTO. | Triển khai plan [FT016 — Scan Service Boundary Cleanup](./features/016-scan-service-boundary-cleanup/03-plan.md). | ⏳ `READY` (Planned) |
| **`TD-002`** | `gateway-service` | 🟡 MEDIUM | Mã nguồn Gateway Media Delivery (FT011) cũ vẫn nằm trong codebase. | Kiến trúc Media Delivery đã được thay thế bởi Nginx Direct Media Delivery theo [ADR-005](./adr/ADR-005-nginx-direct-media-delivery.md) (Port `18119`). | Xóa bỏ legacy media routing handler trong `gateway-service` ở đợt Cleanup Gateway. | 📝 `BACKLOG` |
| **`TD-003`** | `scan-service` | 🟡 MEDIUM | Rủi ro Thread Pinning nếu dùng `synchronized` khi bật Virtual Threads. | JVM Virtual Threads bị dính Pinning trên OS Carrier Thread nếu đụng `synchronized` có I/O. | Audit toàn bộ codebase `scan-service`, thay thế `synchronized` bằng `ReentrantLock`. *(Xem [04-thread-pinning-deep-dive.md](../manual/learning/deep-dive/virtual-threads/04-thread-pinning-deep-dive.md))*. | 📝 `BACKLOG` |
| **`TD-004`** | `platform/observability` | 🟢 LOW | Metrics Prometheus (`scan_run_duration_seconds`) chưa được gắn MDC Trace ID. | Phân tán giữa Micrometer metrics và OpenTelemetry Tracing. | Cấu hình MDC Correlation ID vào Micrometer custom Observation Handler. | 📝 `BACKLOG` |
| **`TD-005`** | `catalog-service` | 🟢 LOW | Enum & Status mapping trong Catalog Event chưa versioning chặt chẽ ở DTO level. | Catalog Event P1 dùng String raw cho một số metadata phụ. | Chuẩn hóa Envelope Schema Versioning trong `platform/event-contracts`. | 📝 `BACKLOG` |

---

## ⚙️ 3. Quy Trình Quản Lý & Trả Nợ Kỹ Thuật (Debt Resolution Protocol)

1. **Nguyên tắc Không sửa tự do**: 
   - Không tự ý thực hiện refactor lớn mà không nằm trong kế hoạch ADLC Feature hoặc được phê duyệt rõ ràng.
2. **Quy trình Đăng ký Nợ mới**:
   - Khi phát hiện Code Smell hoặc nợ phát sinh trong quá trình code Feature, Developer/Agent bổ sung 1 dòng mới vào bảng `Active Technical Debt Backlog` với ID `TD-xxx`.
3. **Quy trình Trả nợ (Paydown Protocol)**:
   - Khi triển khai một Feature mới chạm vào Service chứa Debt, xem xét kết hợp trả nợ `TD-xxx` trong cùng Implementation Plan đó và cập nhật trạng thái sang `DONE`.
