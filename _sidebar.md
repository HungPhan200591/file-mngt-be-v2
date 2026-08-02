* [Home](/)

* **manual**
  * [Manual cá nhân](/manual/README.md)
  * **ai-agent**
    * [Guide vận hành với AI Agent](/manual/ai-agent/operating-guide.md)
  * **learning**
    * **backend-v2**
      * [1. Business model](/manual/learning/backend-v2/01-business-model.md)
      * [2. Kiến trúc và technical concept](/manual/learning/backend-v2/02-architecture-technical.md)
      * [3. Use case và data flow](/manual/learning/backend-v2/03-use-cases-data-flow.md)
      * [4. Database map chi tiết](/manual/learning/backend-v2/04-database-map.md)
      * [5. FT013 primer](/manual/learning/backend-v2/05-ft013-primer.md)
      * [6. Đọc flow Scan → Catalog → Query bằng Grafana và Kibana](/manual/learning/backend-v2/06-observability-scan-to-query.md)
      * [Hướng dẫn hiểu Backend V2](/manual/learning/backend-v2/README.md)
  * **operations**
    * [Docsify và GitHub Pages](/manual/operations/docsify-github-pages.md)
    * [Vận hành Backend V2 ở local](/manual/operations/local-runtime.md)
    * [Quan sát Backend V2 ở local](/manual/operations/observability-local.md)
    * [Bản đồ dự án Backend V2](/manual/operations/project-map.md)
    * [Hướng dẫn Vận hành Swagger UI & OpenAPI Contracts](/manual/operations/swagger-ui-openapi.md)
* **docs**
  * [Trạng thái Backend V2](/docs/STATUS.md)
  * **adlc**
    * [ADLC workflow](/docs/adlc/WORKFLOW.md)
  * **adr**
    * [ADR-001: V2 service và data ownership](/docs/adr/ADR-001-v2-service-and-data-ownership.md)
    * [ADR-002: ELK cho structured logging](/docs/adr/ADR-002-elk-structured-logging.md)
    * [ADR-003: Elasticsearch cho media search](/docs/adr/ADR-003-elasticsearch-media-search.md)
    * [ADR-004: Dải port local riêng cho Backend V2](/docs/adr/ADR-004-local-port-allocation.md)
    * [Architecture Decision Records](/docs/adr/README.md)
  * **architecture**
    * [Backend V2 — Tóm tắt](/docs/architecture/01-SUMMARY.md)
    * [Backend V2 — Kế hoạch và High Level Design](/docs/architecture/02-PLAN.md)
    * [Backend V2 — Coding rules](/docs/architecture/03-CODING_RULES.md)
  * **contracts**
    * [Contracts](/docs/contracts/README.md)
    * **events**
      * [`media.file.discovered.v1`](/docs/contracts/events/media.file.discovered.v1.md)
      * [`media.subject.changed.v1`](/docs/contracts/events/media.subject.changed.v1.md)
      * [Kafka event contracts](/docs/contracts/events/README.md)
    * **http**
      * [Gateway HTTP routing contract v1](/docs/contracts/http/gateway-routing-v1.md)
    * **openapi**
      * [REST API contracts](/docs/contracts/openapi/README.md)
  * **features**
    * [Feature documents](/docs/features/README.md)
    * **001-bootstrap-platform**
      * [001 Bootstrap platform](/docs/features/001-bootstrap-platform/01-brief.md)
      * [001 Bootstrap platform — Design](/docs/features/001-bootstrap-platform/02-design.md)
      * [001 Bootstrap platform — Plan](/docs/features/001-bootstrap-platform/03-plan.md)
    * **002-catalog-vertical-slice**
      * [002 Catalog vertical slice](/docs/features/002-catalog-vertical-slice/01-brief.md)
      * [002 Catalog vertical slice — Design](/docs/features/002-catalog-vertical-slice/02-design.md)
      * [002 Catalog vertical slice — Plan](/docs/features/002-catalog-vertical-slice/03-plan.md)
    * **003-e2e-http-harness**
      * [003 E2E HTTP harness](/docs/features/003-e2e-http-harness/01-brief.md)
      * [003 E2E HTTP harness — Design](/docs/features/003-e2e-http-harness/02-design.md)
      * [003 E2E HTTP harness — Plan](/docs/features/003-e2e-http-harness/03-plan.md)
    * **004-scan-preview**
      * [004 Scan preview](/docs/features/004-scan-preview/01-brief.md)
      * [004 Scan preview — Design](/docs/features/004-scan-preview/02-design.md)
      * [004 Scan preview — Plan](/docs/features/004-scan-preview/03-plan.md)
    * **005-scan-approval-outbox**
      * [005 Scan approval outbox](/docs/features/005-scan-approval-outbox/01-brief.md)
      * [005 Scan approval outbox — Design](/docs/features/005-scan-approval-outbox/02-design.md)
      * [005 Scan approval outbox — Plan](/docs/features/005-scan-approval-outbox/03-plan.md)
    * **006-catalog-subject-changed-outbox**
      * [006 Catalog subject changed outbox](/docs/features/006-catalog-subject-changed-outbox/01-brief.md)
      * [006 Catalog subject changed outbox — Design](/docs/features/006-catalog-subject-changed-outbox/02-design.md)
      * [006 Catalog subject changed outbox — Plan](/docs/features/006-catalog-subject-changed-outbox/03-plan.md)
    * **007-query-subject-projection**
      * [007 Query subject projection](/docs/features/007-query-subject-projection/01-brief.md)
      * [007 Query subject projection — Design](/docs/features/007-query-subject-projection/02-design.md)
      * [007 Query subject projection — Plan](/docs/features/007-query-subject-projection/03-plan.md)
    * **008-elasticsearch-media-search**
      * [008 Elasticsearch media search](/docs/features/008-elasticsearch-media-search/01-brief.md)
      * [008 Elasticsearch media search — Design](/docs/features/008-elasticsearch-media-search/02-design.md)
      * [008 Elasticsearch media search — Plan](/docs/features/008-elasticsearch-media-search/03-plan.md)
    * **009-query-detail-redis-cache**
      * [009 Query detail Redis cache](/docs/features/009-query-detail-redis-cache/01-brief.md)
      * [009 Query detail Redis cache — Design](/docs/features/009-query-detail-redis-cache/02-design.md)
      * [009 Query detail Redis cache — Plan](/docs/features/009-query-detail-redis-cache/03-plan.md)
    * **010-gateway-routing-correlation-id**
      * [010 Gateway routing và correlation ID](/docs/features/010-gateway-routing-correlation-id/01-brief.md)
      * [010 Gateway routing và correlation ID — Design](/docs/features/010-gateway-routing-correlation-id/02-design.md)
      * [010 Gateway routing và correlation ID — Plan](/docs/features/010-gateway-routing-correlation-id/03-plan.md)
    * **011-frontend-gateway-cutover**
      * [011 Frontend Gateway cutover](/docs/features/011-frontend-gateway-cutover/01-brief.md)
      * [011 Frontend Gateway cutover — Design](/docs/features/011-frontend-gateway-cutover/02-design.md)
      * [011 Frontend Gateway cutover — Plan](/docs/features/011-frontend-gateway-cutover/03-plan.md)
    * **012-gallery-v2-parity-foundation**
      * [012 Gallery V2 parity foundation — Brief](/docs/features/012-gallery-v2-parity-foundation/01-brief.md)
      * [012 Gallery V2 parity foundation — Design](/docs/features/012-gallery-v2-parity-foundation/02-design.md)
      * [012 Gallery V2 parity foundation — Plan](/docs/features/012-gallery-v2-parity-foundation/03-plan.md)
    * **013-media-worker-processing-foundation**
      * [013 Media Worker processing foundation](/docs/features/013-media-worker-processing-foundation/01-brief.md)
      * [013 Media Worker processing foundation — Design](/docs/features/013-media-worker-processing-foundation/02-design.md)
      * [013 Media Worker processing foundation — Plan](/docs/features/013-media-worker-processing-foundation/03-plan.md)
    * **014-observability-performance-foundation**
      * [014 Observability và performance foundation](/docs/features/014-observability-performance-foundation/01-brief.md)
      * [014 Observability và performance foundation — Design](/docs/features/014-observability-performance-foundation/02-design.md)
      * [014 Observability và performance foundation — Plan](/docs/features/014-observability-performance-foundation/03-plan.md)
  * **templates**
    * [ADR-<number>: <decision>](/docs/templates/ADR.md)
    * [<Feature title>](/docs/templates/FEATURE_BRIEF.md)
    * [<Feature title> — Design](/docs/templates/FEATURE_DESIGN.md)
    * [<Feature title> — Plan](/docs/templates/FEATURE_PLAN.md)

---
_Generated from repository Markdown. Local output and dependencies are excluded._
