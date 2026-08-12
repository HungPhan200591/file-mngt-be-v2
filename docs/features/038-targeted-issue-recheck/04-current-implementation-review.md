# FT-038 — Review triển khai hiện tại

API kiểm tra issue tồn tại, tạo durable `scan_issue_recheck_job`, trả `202 + jobId`. Worker claim lease 60 giây, resolve configured root và relative path từ server-side issue data, phân tích đúng một file, ghi observation `scan_run`/inventory/proposal/issue và enqueue projection task. File mất được phân loại `MISSING`/`FILE_NOT_FOUND`; client không được gửi absolute path.

Khoảng trống quan trọng: complete/fail chưa conditional theo `leaseOwner`/attempt, enqueue chưa idempotent theo request key. Worker cũ sau reclaim có thể ghi đè state worker mới. Đây là `TD-006`/`TD-012`; cần test path race, missing file, Catalog timeout, duplicate request, reclaim và projection refresh trước production.
