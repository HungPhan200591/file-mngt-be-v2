* [🏠 Trang chủ](README.md)
* [📌 Trạng thái hiện tại](docs/STATUS.md)

* **📖 Manual cá nhân**
  * [Mục lục Manual](manual/README.md)
  * **Học Backend V2**
    * [01. Business model](manual/learning/backend-v2/01-business-model.md)
    * [02. Kiến trúc & Technical](manual/learning/backend-v2/02-architecture-technical.md)
    * [03. Use case & Data flow](manual/learning/backend-v2/03-use-cases-data-flow.md)
    * [04. Database Map](manual/learning/backend-v2/04-database-map.md)
    * [05. FT013 primer](manual/learning/backend-v2/05-ft013-primer.md)
  * **Vận hành dự án**
    * [Bản đồ dự án](manual/operations/project-map.md)
    * [Vận hành local](manual/operations/local-runtime.md)
  * **AI Agent**
    * [Hướng dẫn vận hành AI](manual/ai-agent/operating-guide.md)

* **🏗️ Kiến trúc & Quy định (Docs)**
  * [Quy tắc AGENTS.md](AGENTS.md)
  * **Architecture**
    * [01. Summary](docs/architecture/01-SUMMARY.md)
    * [02. Plan](docs/architecture/02-PLAN.md)
    * [03. Coding Rules](docs/architecture/03-CODING_RULES.md)
  * **ADLC Workflow**
    * [ADLC Workflow](docs/adlc/WORKFLOW.md)
  * **Decisions (ADR)**
    * [ADR-001: Service & Data Ownership](docs/adr/ADR-001-v2-service-and-data-ownership.md)
    * [ADR-002: ELK Structured Logging](docs/adr/ADR-002-elk-structured-logging.md)
    * [ADR-003: Elasticsearch Media Search](docs/adr/ADR-003-elasticsearch-media-search.md)
    * [ADR-004: Local Port Allocation](docs/adr/ADR-004-local-port-allocation.md)

* **🧩 Service Contexts (Apps)**
  * [Gateway Service](apps/gateway-service/CONTEXT.md)
  * [Catalog Service](apps/catalog-service/CONTEXT.md)
  * [Scan Service](apps/scan-service/CONTEXT.md)
  * [Query Service](apps/query-service/CONTEXT.md)
  * [Media Worker](apps/media-worker/CONTEXT.md)

* **🔌 Contracts**
  * **HTTP**
    * [Gateway Routing v1](docs/contracts/http/gateway-routing-v1.md)
  * **Events**
    * [Media File Discovered v1](docs/contracts/events/media.file.discovered.v1.md)
    * [Media Subject Changed v1](docs/contracts/events/media.subject.changed.v1.md)

* **🚀 Features (001 - 013)**
  * [001. Bootstrap Platform](docs/features/001-bootstrap-platform/01-brief.md)
  * [002. Catalog Vertical Slice](docs/features/002-catalog-vertical-slice/01-brief.md)
  * [003. E2E HTTP Harness](docs/features/003-e2e-http-harness/01-brief.md)
  * [004. Scan Preview](docs/features/004-scan-preview/01-brief.md)
  * [005. Scan Approval Outbox](docs/features/005-scan-approval-outbox/01-brief.md)
  * [006. Catalog Subject Changed Outbox](docs/features/006-catalog-subject-changed-outbox/01-brief.md)
  * [007. Query Subject Projection](docs/features/007-query-subject-projection/01-brief.md)
  * [008. Elasticsearch Media Search](docs/features/008-elasticsearch-media-search/01-brief.md)
  * [009. Query Detail Redis Cache](docs/features/009-query-detail-redis-cache/01-brief.md)
  * [010. Gateway Routing Correlation ID](docs/features/010-gateway-routing-correlation-id/01-brief.md)
  * [011. Frontend Gateway Cutover](docs/features/011-frontend-gateway-cutover/01-brief.md)
  * [012. Gallery V2 Parity Foundation](docs/features/012-gallery-v2-parity-foundation/01-brief.md)
  * [013. Media Worker Processing Foundation](docs/features/013-media-worker-processing-foundation/01-brief.md)

* **🧪 Testing**
  * [E2E Testing Harness](tests/e2e/README.md)
