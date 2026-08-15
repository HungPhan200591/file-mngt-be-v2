# Claims

- Network timeout không chứng minh server chưa xử lý.
- Idempotency key biến retry thành thao tác an toàn.
- Server nên lưu kết quả theo key và từ chối cùng key với payload khác.
- Backoff cần jitter.
