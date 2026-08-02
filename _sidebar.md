* [🏠 Trang chủ](README.md)

* **APPS**
  * **CATALOG SERVICE**
    * [Catalog Service context](apps/catalog-service/CONTEXT.md)
  * **GATEWAY SERVICE**
    * [Gateway Service context](apps/gateway-service/CONTEXT.md)
  * **MEDIA WORKER**
    * [Media Worker context](apps/media-worker/CONTEXT.md)
  * **QUERY SERVICE**
    * [Query Service context](apps/query-service/CONTEXT.md)
  * **SCAN SERVICE**
    * [Scan Service context](apps/scan-service/CONTEXT.md)
* **DOCS**
  * **ADLC**
    * [ADLC workflow](docs/adlc/WORKFLOW.md)
  * **ADR**
    * [Architecture Decision Records](docs/adr/README.md)
    * [ADR-001: V2 service và data ownership](docs/adr/ADR-001-v2-service-and-data-ownership.md)
    * [ADR-002: ELK cho structured logging](docs/adr/ADR-002-elk-structured-logging.md)
    * [ADR-003: Elasticsearch cho media search](docs/adr/ADR-003-elasticsearch-media-search.md)
    * [ADR-004: Dải port local riêng cho Backend V2](docs/adr/ADR-004-local-port-allocation.md)
  * **ARCHITECTURE**
    * [Backend V2 — Tóm tắt](docs/architecture/01-SUMMARY.md)
    * [Backend V2 — Kế hoạch và High Level Design](docs/architecture/02-PLAN.md)
    * [Backend V2 — Coding rules](docs/architecture/03-CODING_RULES.md)
  * **CONTRACTS**
    * [Contracts](docs/contracts/README.md)
    * **EVENTS**
      * [Kafka event contracts](docs/contracts/events/README.md)
      * [media.file.discovered.v1](docs/contracts/events/media.file.discovered.v1.md)
      * [media.subject.changed.v1](docs/contracts/events/media.subject.changed.v1.md)
    * **HTTP**
      * [Gateway HTTP routing contract v1](docs/contracts/http/gateway-routing-v1.md)
    * **OPENAPI**
      * [REST API contracts](docs/contracts/openapi/README.md)
  * **FEATURES**
    * [Feature documents](docs/features/README.md)
    * **001 BOOTSTRAP PLATFORM**
      * [001 Bootstrap platform](docs/features/001-bootstrap-platform/01-brief.md)
      * [001 Bootstrap platform — Design](docs/features/001-bootstrap-platform/02-design.md)
      * [001 Bootstrap platform — Plan](docs/features/001-bootstrap-platform/03-plan.md)
    * **002 CATALOG VERTICAL SLICE**
      * [002 Catalog vertical slice](docs/features/002-catalog-vertical-slice/01-brief.md)
      * [002 Catalog vertical slice — Design](docs/features/002-catalog-vertical-slice/02-design.md)
      * [002 Catalog vertical slice — Plan](docs/features/002-catalog-vertical-slice/03-plan.md)
    * **003 E2E HTTP HARNESS**
      * [003 E2E HTTP harness](docs/features/003-e2e-http-harness/01-brief.md)
      * [003 E2E HTTP harness — Design](docs/features/003-e2e-http-harness/02-design.md)
      * [003 E2E HTTP harness — Plan](docs/features/003-e2e-http-harness/03-plan.md)
    * **004 SCAN PREVIEW**
      * [004 Scan preview](docs/features/004-scan-preview/01-brief.md)
      * [004 Scan preview — Design](docs/features/004-scan-preview/02-design.md)
      * [004 Scan preview — Plan](docs/features/004-scan-preview/03-plan.md)
    * **005 SCAN APPROVAL OUTBOX**
      * [005 Scan approval outbox](docs/features/005-scan-approval-outbox/01-brief.md)
      * [005 Scan approval outbox — Design](docs/features/005-scan-approval-outbox/02-design.md)
      * [005 Scan approval outbox — Plan](docs/features/005-scan-approval-outbox/03-plan.md)
    * **006 CATALOG SUBJECT CHANGED OUTBOX**
      * [006 Catalog subject changed outbox](docs/features/006-catalog-subject-changed-outbox/01-brief.md)
      * [006 Catalog subject changed outbox — Design](docs/features/006-catalog-subject-changed-outbox/02-design.md)
      * [006 Catalog subject changed outbox — Plan](docs/features/006-catalog-subject-changed-outbox/03-plan.md)
    * **007 QUERY SUBJECT PROJECTION**
      * [007 Query subject projection](docs/features/007-query-subject-projection/01-brief.md)
      * [007 Query subject projection — Design](docs/features/007-query-subject-projection/02-design.md)
      * [007 Query subject projection — Plan](docs/features/007-query-subject-projection/03-plan.md)
    * **008 ELASTICSEARCH MEDIA SEARCH**
      * [008 Elasticsearch media search](docs/features/008-elasticsearch-media-search/01-brief.md)
      * [008 Elasticsearch media search — Design](docs/features/008-elasticsearch-media-search/02-design.md)
      * [008 Elasticsearch media search — Plan](docs/features/008-elasticsearch-media-search/03-plan.md)
    * **009 QUERY DETAIL REDIS CACHE**
      * [009 Query detail Redis cache](docs/features/009-query-detail-redis-cache/01-brief.md)
      * [009 Query detail Redis cache — Design](docs/features/009-query-detail-redis-cache/02-design.md)
      * [009 Query detail Redis cache — Plan](docs/features/009-query-detail-redis-cache/03-plan.md)
    * **010 GATEWAY ROUTING CORRELATION ID**
      * [010 Gateway routing và correlation ID](docs/features/010-gateway-routing-correlation-id/01-brief.md)
      * [010 Gateway routing và correlation ID — Design](docs/features/010-gateway-routing-correlation-id/02-design.md)
      * [010 Gateway routing và correlation ID — Plan](docs/features/010-gateway-routing-correlation-id/03-plan.md)
    * **011 FRONTEND GATEWAY CUTOVER**
      * [011 Frontend Gateway cutover](docs/features/011-frontend-gateway-cutover/01-brief.md)
      * [011 Frontend Gateway cutover — Design](docs/features/011-frontend-gateway-cutover/02-design.md)
      * [011 Frontend Gateway cutover — Plan](docs/features/011-frontend-gateway-cutover/03-plan.md)
    * **012 GALLERY V2 PARITY FOUNDATION**
      * [012 Gallery V2 parity foundation — Brief](docs/features/012-gallery-v2-parity-foundation/01-brief.md)
      * [012 Gallery V2 parity foundation — Design](docs/features/012-gallery-v2-parity-foundation/02-design.md)
      * [012 Gallery V2 parity foundation — Plan](docs/features/012-gallery-v2-parity-foundation/03-plan.md)
    * **013 MEDIA WORKER PROCESSING FOUNDATION**
      * [013 Media Worker processing foundation](docs/features/013-media-worker-processing-foundation/01-brief.md)
      * [013 Media Worker processing foundation — Design](docs/features/013-media-worker-processing-foundation/02-design.md)
      * [013 Media Worker processing foundation — Plan](docs/features/013-media-worker-processing-foundation/03-plan.md)
  * **TEMPLATES**
    * [ADR-<number>: <decision>](docs/templates/ADR.md)
    * [<Feature title>](docs/templates/FEATURE_BRIEF.md)
    * [<Feature title> — Design](docs/templates/FEATURE_DESIGN.md)
    * [<Feature title> — Plan](docs/templates/FEATURE_PLAN.md)
  * [Trạng thái Backend V2](docs/STATUS.md)
* **GEMINI**
  * **DEEP DIVE**
    * [Backend V2 — Deep-Dive High Level Design](gemini/deep-dive/HIGH-LEVEL-DESIGN.md)
* **MANUAL**
  * [Manual cá nhân](manual/README.md)
  * **AI AGENT**
    * [Guide vận hành với AI Agent](manual/ai-agent/operating-guide.md)
  * **LEARNING**
    * **BACKEND V2**
      * [Hướng dẫn hiểu Backend V2](manual/learning/backend-v2/README.md)
      * [1. Business model](manual/learning/backend-v2/01-business-model.md)
      * [2. Kiến trúc và technical concept](manual/learning/backend-v2/02-architecture-technical.md)
      * [3. Use case và data flow](manual/learning/backend-v2/03-use-cases-data-flow.md)
      * [4. Database map chi tiết](manual/learning/backend-v2/04-database-map.md)
      * [5. FT013 primer](manual/learning/backend-v2/05-ft013-primer.md)
  * **OPERATIONS**
    * [Vận hành Backend V2 ở local](manual/operations/local-runtime.md)
    * [Bản đồ dự án Backend V2](manual/operations/project-map.md)
* **PLATFORM**
  * **EVENT CONTRACTS**
    * [Event contracts module](platform/event-contracts/README.md)
  * **TEST SUPPORT**
    * [Test support module](platform/test-support/README.md)
* **TESTS**
  * **E2E**
    * [E2E HTTP harness](tests/e2e/README.md)
* [Quy tắc Backend V2](AGENTS.md)