# Gateway Service context

## Scope

Route API V2 cho frontend, propagation HTTP correlation ID, timeout và cross-cutting tối thiểu.

## Owns

- Route configuration.
- API composition chỉ khi không thể để frontend gọi các query endpoint riêng.
- Correlation ID HTTP: canonical `X-Correlation-Id`, MDC request scope và cleanup.

## Does not own

- Domain logic, database entity, Kafka business consumer/producer.
- Authentication/permission phức tạp ở bản đầu.

## Dependencies

- Downstream REST contract trong `docs/contracts/openapi/` và ingress contract trong `docs/contracts/http/gateway-routing-v1.md`.
- Gateway không truy cập PostgreSQL/Redis business data.

## Invariants

- Không che lỗi downstream bằng response thành công giả.
- Chỉ route path được liệt kê trong ingress contract; operation/downstream Actuator không đi qua Gateway.
- Timeout phải explicit; Gateway v1 không retry request.
- Browser CORS local dùng allow-list hẹp cho `localhost:8888`/`127.0.0.1:8888`; chi tiết header nằm trong Gateway HTTP contract.
- Gateway dùng correlation filter riêng để canonicalize request chuyển tiếp; auto-filter của
  `platform/observability` phải tắt tại service này.
- Expose `health,info,metrics,prometheus` chỉ trên direct service port; ECS JSON file log không phụ
  thuộc ELK availability.
