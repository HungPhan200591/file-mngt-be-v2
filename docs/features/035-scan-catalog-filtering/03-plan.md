# FT-035 — Scan–Catalog filtering — Plan

Status: IMPLEMENTED — verification deferred
Scale/cloud guide: [05-scale-and-cloud-rollout.md](./05-scale-and-cloud-rollout.md)

1. Thêm HTTP adapter dùng contract FT-034, timeout bounded và validate response theo `clientRef`.
2. Filter proposal trong Scan executor sau analyze, trước chunk persistence; split micro-batch 500.
3. Exact locator skip; các result khác giữ proposal và ghi evidence; không đổi issue/approval/outbox.
4. Ghi decision/evidence này vào SC-01, Scan context và STATUS. Không mở FE/Gateway trong feature.

Verification deferred: compile, Testcontainers, Catalog outage/timeout, response protocol mismatch, query
count và run recovery. Không chuyển `DONE` trước evidence đó.
