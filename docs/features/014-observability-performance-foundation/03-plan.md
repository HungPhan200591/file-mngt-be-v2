# 014 Observability và performance foundation — Plan

Status: READY
Design: [02-design.md](./02-design.md)

## Execution capsule

- Owner: `platform/observability`; `infra/observability` sở hữu Compose/config; năm service sở hữu instrumentation; Gateway là request entry.
- Scope/files: parent/app POM; module `platform/observability`; năm `application.yml`; `infra/compose/compose.yaml`; config Prometheus/Grafana/Logstash/Kibana; `.env.example`; operation docs và owner contexts.
- Must preserve: không đổi business API/event/database; Actuator không đi qua Gateway; observability stack tắt không làm app fail; Elasticsearch logs tách media index; metric label cardinality thấp; không log secret/absolute media path; version/port pin theo source of truth; không triển khai FT013 trong feature này.
- Read on demand: `docs/architecture/03-CODING_RULES.md`, ADR-002/003/004, năm service context, metric classes hiện có, Gateway correlation filter, `infra/compose/README.md`; dùng `$find-docs` cho Spring Boot 4 structured logging/Micrometer và version tool trước khi code config.

## Bước triển khai

1. **Shared foundation:** thêm `platform/observability` vào reactor; cung cấp downstream HTTP correlation MDC scope/cleanup và test không rò MDC, không đưa domain code vào module.
2. **Application metrics/logging:** thêm Prometheus registry cho năm app; expose `health,info,prometheus,metrics` trực tiếp; thêm common service tag và ECS JSON file logging có toggle/path local.
3. **Prometheus/Grafana:** thêm profile `observability`, scrape năm service qua host bridge, persistent volume, provision Prometheus datasource và dashboard overview với stable UID.
4. **ELK:** dùng Elasticsearch đã pin cho cả profile `search`/`observability`; thêm Logstash/Kibana cùng Elastic version, pipeline ingest ECS vào logs data stream và data view/provisioning tối thiểu.
5. **Static/runtime verification:** test common module, app context, Prometheus targets, dashboard queries và Logstash ingest/correlation search; không chạy infrastructure nếu người dùng chưa cho phép.
6. **Operations/docs:** cập nhật `.env.example`, Compose README, năm owner context, local runtime runbook, `docs/STATUS.md` và Plan evidence sau khi người dùng xác minh runtime.

## Kiểm tra

- Static: `git diff --check`, source dưới 500 dòng, Compose config hợp lệ, version không dùng `latest`, port đúng ADR-004 và không có secret/path local commit.
- Unit: correlation ID vào MDC và cleanup cả success/exception; tag convention không tạo high-cardinality value.
- App integration: `/actuator/prometheus` trả text format ở cả năm service; operation endpoint không xuất hiện qua Gateway route.
- Metrics runtime: Prometheus có năm target `up = 1`; dashboard hiển thị HTTP/JVM/Hikari và custom metric hiện hữu khi tạo traffic.
- Logs runtime: ECS JSON parse được; Kibana lọc cùng `correlationId` qua Gateway và downstream; tắt Logstash/Elasticsearch không làm request lỗi.

## Rollout và rollback

- Rollout theo hai lát độc lập: metrics/dashboard → logs/Kibana. Mỗi lát dùng cấu hình opt-in và xác minh trước lát sau.
- Rollback runtime bằng cách không bật profile `observability` và tắt structured file logging. Application vẫn giữ Actuator health hiện tại.
- Rollback code có thể bỏ Prometheus registry/common module và application config mà không migration hoặc sửa domain data.
- Volume Prometheus/ELK là disposable local data; chỉ xóa khi người dùng yêu cầu rõ.

## Tài liệu cần cập nhật

- Khi implementation: `infra/compose/README.md`, `manual/operations/local-runtime.md`, năm owner `CONTEXT.md`, `docs/STATUS.md` và evidence trong Plan.
- ADR-002/003 giữ quyết định ELK/search đã chốt; ADR-004 đã dành đủ port nên không cần ADR mới hoặc đổi port.
- Không cập nhật REST/Kafka contract vì FT014 không đổi business boundary.
