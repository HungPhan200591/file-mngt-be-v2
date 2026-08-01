# Gateway Service context

## Scope

Route API V2 cho frontend, propagation correlation ID, timeout và cross-cutting tối thiểu.

## Owns

- Route configuration.
- API composition chỉ khi không thể để frontend gọi các query endpoint riêng.
- Correlation ID HTTP/Kafka header.

## Does not own

- Domain logic, database entity, Kafka business consumer/producer.
- Authentication/permission phức tạp ở bản đầu.

## Dependencies

- Downstream REST contract trong `docs/contracts/openapi/`.
- Gateway không truy cập PostgreSQL/Redis business data.

## Invariants

- Không che lỗi downstream bằng response thành công giả.
- Timeout/retry phải explicit và chỉ áp dụng cho request idempotent.
