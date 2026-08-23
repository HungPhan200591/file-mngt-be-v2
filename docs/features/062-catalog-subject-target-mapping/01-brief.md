# FT-062 — Catalog Subject Target Mapping

Owner: `catalog-service`

## Vấn đề

FT-061 đã loại parent-row contention khỏi V59 ingest và pass 25K x3 với bốn ingest workers. Physical 1M vẫn
vượt 110 giây. Runtime inspection cho thấy PostgreSQL đang active tại câu
`update benchmark_catalog_subject_reduction set subject_id = ...` trong `bulkUpsertRange`, không chờ lock.

Physical driver đang mutate lại toàn scratch reduction theo từng worker, trong khi production V23 đã dùng
`tmp_catalog_target(subject_key, subject_id)` riêng. Shape benchmark vì vậy tạo table rewrite/WAL/page
contention không đại diện cho production target mapping.

## Mục tiêu và acceptance criteria

- Bỏ mutable `subject_id` khỏi subject reduction benchmark.
- Upsert subject và lấy mapping bằng `INSERT ... ON CONFLICT DO UPDATE ... RETURNING` trong cùng statement.
- Ghi mapping vào scratch target riêng; downstream asset/tag/actress chỉ join target.
- Giữ bốn ingest workers, hai upsert workers, phase tuần tự và PostgreSQL durability mặc định.
- 25K x3 phải exact cardinality, zero deadlock/lock waiter/sampler failure.
- Physical 1M `<=90s` mới cho phép chạy combined; `>90s` dừng và ghi bottleneck kế tiếp.

## Ngoài phạm vi

- Không đổi FT-061 ingest fence, scheduler, logical completion shard hoặc event contract.
- Không tăng worker, overlap phase, tắt durability hay sửa timeout.
- Không sửa migration production V23 trong feature này: V23 đã có target-table shape tương ứng.
- Không chạy combined trước khi physical gate đạt.

## Rủi ro

- `ON CONFLICT DO UPDATE` vẫn phải serialize nếu hai workers cùng subject; routing ranges phải tuyệt đối disjoint.
- Target mapping phải chứa cả subject insert mới và subject đã tồn tại.
- Bỏ reduction mutation không được làm mất asset/tag/actress cardinality hoặc primary election.
