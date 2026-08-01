---
name: adlc-feature-delivery
description: Thực hiện feature Backend V2 theo ADLC, từ tạo hoặc đọc Brief, Design, Plan đến triển khai và bàn giao. Dùng khi thêm chức năng, thay đổi nghiệp vụ, tạo service/module, hoặc người dùng yêu cầu làm theo feature; không dùng cho sửa typo hay thay đổi cục bộ không đổi contract.
---

# ADLC feature delivery

## Nạp đúng ngữ cảnh

1. Đọc `AGENTS.md`, `docs/STATUS.md`, `docs/architecture/01-SUMMARY.md`.
2. Xác định owner; đọc đúng một `apps/<service>/CONTEXT.md`.
3. Đọc `docs/features/<feature-id>/` nếu đã tồn tại. Chỉ đọc contract/ADR trực tiếp liên quan.

## Nếu feature chưa có tài liệu

1. Tạo folder `docs/features/<feature-id>/` từ ba template.
2. Brief ghi acceptance criteria và ngoài phạm vi.
3. Design ghi owner, data ownership, API/event, failure/idempotency và rủi ro.
4. Plan ghi bước file/module, verify và rollback.
5. Chỉ đặt `Status` trong Plan: `READY` khi không còn quyết định nghiệp vụ/kiến trúc quan trọng; nếu còn thì giữ `DRAFT` và nêu câu hỏi.

## Triển khai

- Chỉ code khi Plan là `READY`.
- Giữ phạm vi trong owner. Nếu chạm REST/Kafka/database hoặc hai service, dùng thêm `$cross-service-contract`.
- Không nhét rule feature vào `AGENTS.md` hoặc context service.
- Sau handoff, cập nhật Plan thành `DONE` cùng kiểm tra đã chạy; cập nhật `docs/STATUS.md` nếu feature active đổi.

## Bàn giao

Nêu feature doc, owner thay đổi, contract có đổi hay không, kiểm tra đã chạy và việc cần người dùng cho phép.
