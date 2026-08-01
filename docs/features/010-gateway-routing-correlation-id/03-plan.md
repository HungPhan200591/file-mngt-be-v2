# 010 Gateway routing và correlation ID — Plan

Status: DONE

## Evidence

- `mvnw -pl apps/gateway-service -am test` pass với JDK 25: 5 integration tests cho routing, canonical correlation ID, deny operation, 504 timeout, 502 connect failure và không retry.
- Runtime E2E đã thêm `gateway/001-routing-correlation.http` và `npm run gateway:local`; chạy sau khi khởi động Gateway, Catalog và Query.
Design: [02-design.md](./02-design.md)

## Execution capsule

- Owner: `gateway-service`; Catalog/Scan/Query giữ nguyên business contract và data ownership.
- Scope/files: Maven version/dependency, Gateway route/client/correlation config, integration tests, HTTP contract, E2E `.http`, Gateway context và trạng thái dự án.
- Must preserve: direct service ports vẫn dùng được; operations endpoint không qua Gateway; downstream status/body không bị che; không retry mutation; không database/Kafka/domain logic; port theo ADR-004; source dưới 500 dòng.
- Read on demand: Gateway context, `gateway-routing-v1.md`, ba OpenAPI hiện hành, ADR-004, official Spring Cloud Gateway 5 Server Web MVC docs và HLD trong Design.

## Bước triển khai

1. Dependency: pin Spring Cloud Gateway 5.0.3 ở Maven root; thay Web starter của `gateway-service` bằng `spring-cloud-starter-gateway-server-webmvc`, giữ Actuator và không nhập thêm WebFlux.
2. Routing: cấu hình ba downstream URI bằng environment và route đúng Catalog subjects, Scan, Query subjects; giữ nguyên path, không route operations/Actuator.
3. Correlation: thêm policy/filter tạo hoặc giữ `X-Correlation-Id`, canonicalize outbound header, trả response header, set/cleanup MDC và xử lý duplicate/invalid input.
4. HTTP client/error: cấu hình connect/response timeout riêng cho Gateway; giữ downstream 4xx/5xx, map connect failure `502`, timeout `504`, không retry.
5. Verification: integration test với local stub server cho route/path/query/body/header/status, generated/preserved correlation ID, operations 404, timeout/failure và đúng một downstream attempt.
6. Runtime E2E/docs: thêm `gatewayBaseUrl` và `gateway/001-routing-correlation.http`, npm script dùng chung IntelliJ/httpYac; cập nhật Gateway context, E2E README, manual vận hành, Plan/Status sau evidence.

## Kiểm tra

- Static: `spotless:apply`, `git diff --check`, dependency tree/version audit, link/HLD review và source dưới 500 dòng.
- Gateway integration: routing đủ ba owner; preserve method/path/query/body/status; correlation ID preserve/generate/replace; MDC cleanup; 404/502/504; không retry.
- Regression: chạy toàn bộ `gateway-service` tests; downstream service test không cần đổi vì business API không đổi.
- Runtime sau khi người dùng restart Gateway: `.http` gọi Catalog/Query qua `18100`, xác minh response correlation header và direct service ports vẫn hoạt động.

## Rollout và rollback

- Rollout additive: client thử Gateway `18100` trong khi direct URLs `18101–18103` vẫn giữ nguyên; chưa chuyển frontend hàng loạt trong FT010.
- Rollback bằng cách cho client quay lại direct service URL hoặc bỏ Gateway routes/starter. Không có migration, cache hay durable state cần phục hồi.

## Tài liệu cần cập nhật

- Cập nhật `apps/gateway-service/CONTEXT.md`, `docs/STATUS.md`, E2E README và manual local runtime khi implementation hoàn tất.
- Không sửa ba business OpenAPI, Kafka contract, architecture ownership hay ADR-004 vì path/schema/port hiện hành không đổi; ingress/header nằm tại contract HTTP riêng.
