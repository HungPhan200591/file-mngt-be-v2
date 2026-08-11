# FT-038 — Targeted issue recheck

Owner: `scan-service`; scope: SC-01 BT-06C / TD-006.

## Mục tiêu

Cho phép recheck một issue theo job durable mà không giả dạng full-root scan. Worker tự resolve file từ issue
history và configured root, đọc observation hiện tại, tạo một observation scan run mới và enqueue read-model
refresh.

## Acceptance criteria

- `POST /api/v2/scans/issues/{issueId}/recheck` trả `202` với `jobId`.
- Job có lease/reclaim và terminal `COMPLETED`/`FAILED`; client không truyền absolute path.
- Path traversal bị chặn; file mất được ghi `MISSING` inventory + `FILE_NOT_FOUND` issue.
- Proposal/issue mới thuộc observation run và projection refresh được enqueue.

## Ngoài phạm vi

Không rewalk toàn root, không sửa/xóa issue history, không expose job query UI trong lát này.
