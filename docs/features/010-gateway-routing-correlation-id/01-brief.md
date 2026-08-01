# 010 Gateway routing và correlation ID

Owner: `gateway-service`

## Vấn đề

Frontend và E2E hiện phải biết port riêng của Catalog, Scan và Query. Backend V2 chưa có một ingress ổn định ở port `18100`, đồng thời request chưa có correlation ID thống nhất để nối log giữa Gateway và downstream HTTP call.

## Mục tiêu và acceptance criteria

- Route các business API hiện có qua Gateway mà không đổi method, path, query, request body, response body hoặc downstream status.
- Public route tại Gateway chỉ gồm Catalog subjects, Scan và Query subjects; operations/Actuator của downstream vẫn chỉ gọi trực tiếp ở local.
- Dùng header chuẩn `X-Correlation-Id`: giữ ID hợp lệ từ client, tạo UUID khi thiếu/không hợp lệ, chỉ forward một giá trị canonical và trả cùng giá trị trong response.
- Đặt correlation ID vào MDC trong phạm vi request và luôn cleanup để virtual/platform thread không rò context sang request khác.
- Downstream timeout/connect failure trả 5xx phù hợp, có correlation header; không retry tự động trong feature này.
- Có integration test cho routing, header propagation, status preservation, timeout/failure và route bị từ chối; có `.http` runtime scenario dùng được bằng IntelliJ và CLI.

## Ngoài phạm vi

- Authentication/authorization, rate limit, circuit breaker, retry, service discovery, load balancing, TLS termination và API composition.
- Correlation ID trong Kafka header, OpenTelemetry trace/span, structured JSON logging hoặc frontend cutover.
- Route `/api/v2/catalog/operations/**`, `/api/v2/query/operations/**` hay Actuator của downstream qua Gateway.
- Thay đổi business OpenAPI, database, Kafka event hoặc domain logic của Catalog/Scan/Query.

## Câu hỏi/rủi ro mở

- Không còn quyết định chặn triển khai. Gateway dùng Spring Cloud Gateway Server Web MVC 5.0.3 tương thích Spring Boot 4; timeout ban đầu là connect 1 giây và response 30 giây, đều cấu hình được.
