# Gateway HTTP routing contract v1

Owner: `gateway-service`
Base URL local: `http://localhost:18100`

## Public route map

| Gateway path | Downstream owner | Forwarded path |
| --- | --- | --- |
| `/api/v2/catalog/subjects`, `/api/v2/catalog/subjects/**` | Catalog | Không đổi |
| `/api/v2/scans/**` | Scan | Không đổi |

SC-01 dùng cùng route Scan wildcard cho các endpoint bất đồng bộ mới:
`POST /api/v2/scans/review-queue/decision-jobs`, `POST /api/v2/scans/review-queue/reopen-jobs` và
`POST /api/v2/scans/issues/{issueId}/recheck`. Gateway không unwrap `jobId`; giữ nguyên `202` và response body
để FE đọc theo contract của Scan.
| `/api/v2/query/subjects`, `/api/v2/query/subjects/**` | Query | Không đổi |
| `/api/v2/media/subjects/**` | Media Worker | Legacy FT011; deprecated theo ADR-005, chờ feature migration gỡ code |

Gateway không public `/api/v2/catalog/operations/**`, `/api/v2/query/operations/**` hoặc Actuator của downstream. Các endpoint đó chỉ dùng qua direct local service port cho operation/admin workflow.

## Local browser CORS

Gateway chỉ cho phép origin Nginx V2 `http://localhost:18119`/`http://127.0.0.1:18119` và Vite FE V2 `http://localhost:18120`/`http://127.0.0.1:18120` gọi `/api/v2/**` ở local. Response expose `X-Correlation-Id`; media file được browser tải trực tiếp từ Nginx V2 theo ADR-005, không qua Gateway. Không dùng wildcard origin, credentials hoặc origin V1 `8888`.

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

## SSE streaming route

- `/api/v2/scans/{scanId}/events` được forward nguyên path/status/content type và
  từng frame `text/event-stream`; Gateway không buffer toàn bộ response chờ terminal.
- Scan gửi heartbeat comment tối đa mỗi 15 giây, nhỏ hơn read timeout 30 giây. Nếu
  downstream im lặng quá timeout trước frame đầu, Gateway trả `504`; sau khi response
  đã commit, lỗi/timeout đóng connection và client phục hồi qua REST/reconnect.
- Response mở đầu vẫn có một `X-Correlation-Id` canonical. Correlation ID là của
  connection, không được dùng làm SSE event ID hoặc metric label.
- Contract không cam kết replay `Last-Event-ID`, multi-instance fan-out hoặc custom
  authorization header cho native browser `EventSource`.
