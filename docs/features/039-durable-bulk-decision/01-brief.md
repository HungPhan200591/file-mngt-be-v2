# FT-039 — Durable bulk decision

Owner: `scan-service`; scope: SC-01 BT-07.

## Mục tiêu

Đưa bulk approve/reject/reopen vào persisted job, tránh request đồng bộ materialize toàn queue và transaction
không bounded.

## Acceptance criteria

- Endpoint async trả `202` + `jobId`; worker claim lease và xử lý batch projection hiện hữu mỗi transaction.
- Progress được cộng dồn; job có terminal `COMPLETED`/`FAILED`; approval vẫn ghi decision + outbox cùng transaction.
- API bulk cũ được giữ tương thích trong lát chuyển tiếp; consumer mới dùng decision-jobs.

## Ngoài phạm vi

Không đổi event payload, không xóa API cũ, không thêm FE mapping/job UI trong feature này.
