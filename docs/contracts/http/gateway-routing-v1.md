# Gateway HTTP routing contract v1

Owner: `gateway-service`
Base URL local: `http://localhost:18100`

## Public route map

| Gateway path | Downstream owner | Forwarded path |
| --- | --- | --- |
| `/api/v2/catalog/subjects`, `/api/v2/catalog/subjects/**` | Catalog | Không đổi |
| `/api/v2/scans/**` | Scan | Không đổi |
| `/api/v2/query/subjects`, `/api/v2/query/subjects/**` | Query | Không đổi |
| `/api/v2/media/subjects/**` | Media Worker | Legacy FT011; deprecated theo ADR-005, chờ feature migration gỡ code |

Gateway không public `/api/v2/catalog/operations/**`, `/api/v2/query/operations/**` hoặc Actuator của downstream. Các endpoint đó chỉ dùng qua direct local service port cho operation/admin workflow.

## Local browser CORS

Gateway chỉ cho phép origin `http://localhost:8888` và `http://127.0.0.1:8888` gọi `/api/v2/**` ở local. Response expose `X-Correlation-Id`; media file được browser tải trực tiếp từ Nginx theo ADR-005, không qua Gateway. Không dùng wildcard origin hoặc credentials.

## Correlation header

- Header: `X-Correlation-Id`.
- Giá trị hợp lệ: 1–64 ký tự thuộc `[A-Za-z0-9._-]`.
- Một header hợp lệ từ client được giữ nguyên.
- Header thiếu, có nhiều giá trị hoặc không hợp lệ được thay bằng UUID do Gateway tạo.
- Gateway forward đúng một giá trị canonical đến downstream và trả cùng giá trị trong mọi response, kể cả Gateway `404`, connect failure hoặc timeout.
- Downstream không được quyết định lại correlation ID cho request đã đi qua Gateway.

## Compatibility và error

- Gateway giữ nguyên HTTP method, path, query, request body/content type và downstream business status/body.
- Connect failure trả `502`; response timeout trước khi response commit trả `504`. Nếu downstream treo sau khi Gateway đã forward một phần body thì Gateway đóng response vì HTTP không còn cho phép đổi status. Gateway không retry trong contract v1.
- Contract này additive; direct service URLs và business OpenAPI v1 vẫn hợp lệ trong giai đoạn chuyển tiếp.
- Correlation ID Kafka header, trace/span và authentication không thuộc contract v1 này.
