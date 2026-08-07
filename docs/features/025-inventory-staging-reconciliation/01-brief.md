# FT-025 — Inventory staging reconciliation

Owner: `scan-service`

Baseline: [FT-024 — Inventory Matcher](../024-inventory-matcher/03-plan.md)

## Vấn đề

FT-024 bỏ parser cho file không đổi nhưng vẫn upsert toàn bộ `scan_file_inventory` để đổi `last_seen_run_id`. Run warm scan một triệu file ngày 2026-08-07 đã tạo `0` proposal, `0` issue nhưng vẫn update đúng một triệu inventory row trong khoảng 80 giây. Cột `last_seen_run_id` và index của nó biến phép kiểm tra hiện diện thành write amplification trên toàn bộ root.

Feature này là phần bổ sung sửa giới hạn của FT-024; không sửa hoặc ghi đè tài liệu lịch sử của feature cũ.

## Mục tiêu và acceptance criteria

1. Mọi file nhìn thấy trong run được ghi vào staging bằng PostgreSQL `COPY FROM STDIN` theo chunk bounded-memory.
2. `scan_file_inventory` chỉ insert/update file mới, fingerprint thay đổi hoặc file `MISSING` xuất hiện lại; file `PRESENT` không đổi không bị rewrite.
3. Finalization có lease fencing dùng anti-join staging để mark `MISSING`, dọn staging và complete run trong cùng transaction.
4. Staging của run lỗi hoặc run cũ cùng root được dọn an toàn; mất staging sau database crash không làm inventory canonical sai.
5. Xóa `last_seen_run_id` và index liên quan bằng Flyway migration append-only vì staging sở hữu semantics “đã thấy trong run”.
6. Warm rescan không đổi giữ `proposalCount = 0`, `issueCount = 0`, không thay `updated_at` của inventory và không để lại staging row sau finalization.
7. Không đổi REST API, Kafka event, database ownership hoặc UI behavior.

## Ngoài phạm vi

- Bỏ full filesystem walk hoặc triển khai Windows USN Journal/WatchService.
- Thay batch lookup fingerprint hiện tại bằng set-based diff toàn run.
- Tuyên bố throughput/SLO mới trước khi có benchmark sau triển khai.
- Chạy migration thật, service, test hoặc benchmark khi người dùng chưa yêu cầu.

## Câu hỏi/rủi ro mở

- Số giây cải thiện thực tế phải được benchmark; staging loại bỏ random inventory rewrite nhưng không loại bỏ một triệu filesystem metadata read.
