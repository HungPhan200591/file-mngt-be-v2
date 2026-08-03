# 3. Use Cases và Data Flow (System Overview)

> 📌 **Tài liệu Nguồn chuẩn Features & Specs (SSOT)**:
> Để xem chi tiết thiết kế kỹ thuật của các Feature đã và đang triển khai, tham khảo:
> - 📂 **[Danh mục các Features đang làm (`docs/features/`)](../../../docs/features/)**
> - 📄 **[Thiết kế Scan Preview (FT004)](../../../docs/features/004-scan-preview/02-design.md)**
> - 📄 **[Trạng thái Dự án Hiện tại (`docs/STATUS.md`)](../../../docs/STATUS.md)**

Chương này mô tả cả hệ thống hiện tại lẫn đích dự kiến. Nhãn trạng thái:

- **ĐÃ CÓ**: code/runtime hiện tại đã hỗ trợ.
- **FT013**: đã có Plan `READY`, chưa code.
- **ROADMAP**: có trong kế hoạch kiến trúc nhưng chưa thành feature chi tiết.
- **BUSINESS TARGET**: nhu cầu đích đã biết, contract/database còn phải thiết kế.

## Bản đồ use case đầy đủ

| Nhóm | Use case | Trạng thái | Owner chính |
| --- | --- | --- | --- |
| Ingestion | Cấu hình root/profile | ĐÃ CÓ | Scan / Media Worker config |
| Ingestion | Scan preview, Proposal và Issue | ĐÃ CÓ | Scan |
| Review | Approve/Reject proposal | ĐÃ CÓ | Scan |
| Canonical | Hội tụ file thành Subject/Asset | ĐÃ CÓ | Catalog |
| Processing | Đọc size/MIME/last-modified | FT013 | Media Worker + Catalog |
| Processing | Duration/resolution/codec | ROADMAP | Media Worker |
| Processing | Thumbnail, GIF preview, hash | ROADMAP | Media Worker + Catalog |
| Read | List/detail/filter/order/pagination | ĐÃ CÓ nền | Query |
| Search | Full-text/fuzzy/autocomplete | ĐÃ CÓ nền | Query + Elasticsearch |
| Delivery | GET/HEAD/Range video, ảnh, GIF | ĐÃ CÓ | Media Worker |
| Metadata | Quản lý Actress/Studio/Tag/alias | BUSINESS TARGET | Catalog/Admin |
| Album | Review và liên kết `FULL_ALBUM_OF` | BUSINESS TARGET | Catalog/Admin |
| Migration | Dry-run/import/reconcile dữ liệu V1 | ROADMAP Phase 7 | Importer + Catalog |
| Recovery | Rebuild Query/search projection | ĐÃ CÓ một phần | Query |
| Operations | Metrics, trace và ELK end-to-end | ROADMAP Phase 8 | Toàn hệ thống |
| Frontend | Gallery V2 parity và cutover | DRAFT, làm sau backend | Gallery V2 |

## Toàn bộ lifecycle dự kiến của một media

```mermaid
flowchart TB
    FILE["<font color='white'>1. Filesystem<br/>video / image / GIF</font>"] --> SCAN["<font color='white'>2. Scan + parse<br/>Proposal / Issue</font>"]
    SCAN --> REVIEW["<font color='white'>3. Human review<br/>Approve / Reject</font>"]
    REVIEW --> CAT["<font color='white'>4. Catalog<br/>Subject + Asset</font>"]
    CAT --> PROCESS["<font color='white'>5. Worker processing<br/>metadata + artifacts</font>"]
    PROCESS --> PROJECTION["<font color='white'>6. Query projection<br/>PostgreSQL + search</font>"]
    PROJECTION --> UI["<font color='white'>7. Gallery / Library<br/>browse + filter</font>"]
    UI --> DELIVERY["<font color='white'>8. Media Delivery<br/>preview / play</font>"]
    CAT -.->|metadata edit| META["<font color='white'>Admin<br/>Actress / Studio / Tag</font>"]
    META -.->|new snapshot| PROJECTION

    style FILE fill:#009688,stroke:#fff,stroke-width:2px
    style SCAN fill:#2196F3,stroke:#fff,stroke-width:2px
    style REVIEW fill:#FF9800,stroke:#fff,stroke-width:2px
    style CAT fill:#2196F3,stroke:#fff,stroke-width:2px
    style PROCESS fill:#2196F3,stroke:#fff,stroke-width:2px
    style PROJECTION fill:#9C27B0,stroke:#fff,stroke-width:2px
    style UI fill:#4CAF50,stroke:#fff,stroke-width:2px
    style DELIVERY fill:#FF9800,stroke:#fff,stroke-width:2px
    style META fill:#FF9800,stroke:#fff,stroke-width:2px
```

Các bước 1–4, 6 nền và 8 đã có. FT013 hoàn thành phần đầu của bước 5. Metadata Admin và processing artifact đầy đủ chưa có contract V2.

## UC01 — Cấu hình root và profile (ĐÃ CÓ)

Mục tiêu: hệ thống biết một tên root logic trỏ tới folder nào và phải parse bằng luật nào.

Ví dụ:

```text
rootKey: fixture-joke-video
profile: JOKE_VIDEO
local path: D:/.../tests/fixtures/scan/joke-video
```

- API/event chỉ mang `rootKey`/`storageKey` và relative path.
- Absolute path chỉ nằm trong cấu hình local.
- Scan dùng profile để parse; Worker dùng root registry để đọc file.
- Cùng một key phải trỏ đúng cùng nguồn logic ở các service cần đọc nó.

## UC02 — Scan preview (ĐÃ CÓ)

1. Client gửi `rootKey`.
2. Scan tạo `scan_run` trạng thái `RUNNING` rồi trả `202`.
3. Background task duyệt regular files, bỏ symlink.
4. File parse được tạo `scan_proposal`; file không hiểu tạo `scan_issue`.
5. Run kết thúc `COMPLETED` hoặc `FAILED`, cập nhật counters.

Catalog không thay đổi trong use case này. Người dùng có thể xem proposal/issue trước khi quyết định.

## UC03 — Review và canonical ingestion (ĐÃ CÓ)

```mermaid
flowchart TB
    REVIEW["<font color='white'>APPROVE proposal</font>"] --> TX1["<font color='white'>Scan transaction<br/>decision + outbox</font>"]
    TX1 --> SDB["<font color='white'>scan_db</font>"]
    SDB --> DISCOVERED["<font color='white'>Kafka<br/>file.discovered.v1</font>"]
    DISCOVERED --> TX2["<font color='white'>Catalog transaction<br/>dedupe + upsert</font>"]
    TX2 --> CDB["<font color='white'>catalog_db<br/>Subject + Asset</font>"]
    CDB --> CHANGED["<font color='white'>Kafka<br/>subject.changed.v1</font>"]
    CHANGED --> TX3["<font color='white'>Query transaction<br/>replace snapshot</font>"]
    TX3 --> QDB["<font color='white'>query_db</font>"]

    style REVIEW fill:#4CAF50,stroke:#fff,stroke-width:2px
    style TX1 fill:#FF9800,stroke:#fff,stroke-width:2px
    style SDB fill:#9C27B0,stroke:#fff,stroke-width:2px
    style DISCOVERED fill:#E91E63,stroke:#fff,stroke-width:2px
    style TX2 fill:#FF9800,stroke:#fff,stroke-width:2px
    style CDB fill:#9C27B0,stroke:#fff,stroke-width:2px
    style CHANGED fill:#E91E63,stroke:#fff,stroke-width:2px
    style TX3 fill:#FF9800,stroke:#fff,stroke-width:2px
    style QDB fill:#9C27B0,stroke:#fff,stroke-width:2px
```

### Approve

- Scan ghi Decision và outbox cùng transaction.
- Catalog dedupe event rồi tìm/tạo Subject theo `(region, subjectType, identityKey)`.
- Nếu locator chưa có, Catalog thêm Asset.
- Catalog phát full Subject snapshot; Query chỉ áp dụng version mới hơn.

### Reject

- Scan chỉ ghi `scan_decision = REJECT`.
- Không có discovery event, Catalog và Query không thay đổi.

### Tính nhất quán

API approve trả khi Scan transaction xong, không đợi Catalog/Query. Dữ liệu đọc có thể xuất hiện sau một khoảng ngắn; E2E phải poll có timeout thay vì giả định xuất hiện ngay.

## UC04 — Technical metadata foundation (FT013)

```mermaid
flowchart TB
    ASSET["<font color='white'>Catalog<br/>new Asset + locator</font>"] --> REQUEST["<font color='white'>Same transaction<br/>processing outbox</font>"]
    REQUEST --> JOB["<font color='white'>Kafka<br/>processing.requested.v1</font>"]
    JOB --> WORKER["<font color='white'>Worker<br/>safe file attributes</font>"]
    WORKER --> COMPLETE["<font color='white'>Kafka<br/>processing.completed.v1</font>"]
    COMPLETE --> APPLY["<font color='white'>Catalog transaction<br/>dedupe + metadata</font>"]
    APPLY --> SNAPSHOT["<font color='white'>Subject snapshot<br/>version mới</font>"]
    SNAPSHOT --> QUERY["<font color='white'>Query<br/>metadata projection</font>"]

    style ASSET fill:#2196F3,stroke:#fff,stroke-width:2px
    style REQUEST fill:#9C27B0,stroke:#fff,stroke-width:2px
    style JOB fill:#E91E63,stroke:#fff,stroke-width:2px
    style WORKER fill:#2196F3,stroke:#fff,stroke-width:2px
    style COMPLETE fill:#E91E63,stroke:#fff,stroke-width:2px
    style APPLY fill:#FF9800,stroke:#fff,stroke-width:2px
    style SNAPSHOT fill:#E91E63,stroke:#fff,stroke-width:2px
    style QUERY fill:#2196F3,stroke:#fff,stroke-width:2px
```

Kết quả FT013: `contentLength`, `mediaType`, `sourceLastModifiedAt` và metadata version xuất hiện ở Catalog rồi hội tụ sang Query.

Worker không có database trong feature này. Đọc lại file là an toàn; completion có identity ổn định và Catalog dedupe.

## UC05 — Processing media đầy đủ (ROADMAP sau FT013)

Đây là hướng dự kiến, chưa có feature/contract cuối cùng:

1. Đọc deep metadata: duration, width/height, codec, frame rate khi có giá trị cho UI.
2. Tính content hash để hỗ trợ nhận diện duplicate/integrity.
3. Tạo thumbnail cho video/ảnh khi cần derivative riêng.
4. Tạo GIF/video preview với policy giới hạn dung lượng/thời lượng.
5. Ghi artifact an toàn bằng temp file + atomic move; retry không tạo nhiều bản khác nhau.
6. Catalog nhận completion và lưu locator/role/technical metadata của artifact canonical.
7. Query nhận snapshot mới để Gallery dùng thumbnail/GIF mà không tự suy diễn path.

```mermaid
flowchart TB
    REQUEST["<font color='white'>Processing request<br/>asset + operation version</font>"] --> WORKER["<font color='white'>Worker tool adapter<br/>ffprobe / image / hash</font>"]
    WORKER --> SOURCE["<font color='white'>Source media<br/>read-only</font>"]
    WORKER --> ARTIFACT["<font color='white'>Generated artifacts<br/>deterministic locator</font>"]
    WORKER --> COMPLETE["<font color='white'>Processing completion</font>"]
    COMPLETE --> CAT["<font color='white'>Catalog<br/>canonical metadata/artifacts</font>"]
    CAT --> QUERY["<font color='white'>Query projection</font>"]

    style REQUEST fill:#E91E63,stroke:#fff,stroke-width:2px
    style WORKER fill:#2196F3,stroke:#fff,stroke-width:2px
    style SOURCE fill:#009688,stroke:#fff,stroke-width:2px
    style ARTIFACT fill:#009688,stroke:#fff,stroke-width:2px
    style COMPLETE fill:#E91E63,stroke:#fff,stroke-width:2px
    style CAT fill:#2196F3,stroke:#fff,stroke-width:2px
    style QUERY fill:#2196F3,stroke:#fff,stroke-width:2px
```

Các quyết định còn phải chốt sau FT013: artifact root/layout, reprocessing policy, tool timeout, cleanup file lỗi và liệu Worker có cần durable job state hay không.

## UC06 — Browse, filter và search (ĐÃ CÓ nền, sẽ mở rộng)

```mermaid
flowchart TB
    UI["<font color='white'>Gallery / Library</font>"] --> GW["<font color='white'>Gateway</font>"]
    GW --> API["<font color='white'>Query API</font>"]
    API --> PG["<font color='white'>query_db<br/>list/detail</font>"]
    API --> ES["<font color='white'>Elasticsearch<br/>search/autocomplete</font>"]
    API --> REDIS["<font color='white'>Redis<br/>detail cache</font>"]
    ES -.->|degraded fallback| PG
    REDIS -.->|miss / error| PG

    style UI fill:#4CAF50,stroke:#fff,stroke-width:2px
    style GW fill:#2196F3,stroke:#fff,stroke-width:2px
    style API fill:#FF9800,stroke:#fff,stroke-width:2px
    style PG fill:#9C27B0,stroke:#fff,stroke-width:2px
    style ES fill:#009688,stroke:#fff,stroke-width:2px
    style REDIS fill:#009688,stroke:#fff,stroke-width:2px
```

Hiện đã có subject list/detail, filter nền, full-text/fuzzy/autocomplete, PostgreSQL fallback và Redis detail cache. Sau khi business metadata đầy đủ, Query dự kiến mở rộng filter/order theo Actress, Studio, Tag, media dimensions, availability và Album relation.

## UC07 — Direct media delivery (TARGET theo ADR-005)

1. Browser nhận `mediaUrl` do V2 tạo từ asset locator và root map đáng tin cậy.
2. Browser gọi trực tiếp Nginx bằng URL tương thích V1 `/files/<drive>:/...`.
3. Nginx resolve URL bằng `alias` read-only rồi tự phục vụ full response, HEAD hoặc byte range.
4. Media Worker chỉ dùng `storageKey + relativePath` cho processing nền, không nằm trên đường phát file.
5. Nginx tự trả MIME/header phù hợp.

`Range` cho phép video player tải từng đoạn. Frontend không nhận raw filesystem path.

## UC08 — Metadata management (BUSINESS TARGET)

Mục tiêu dự kiến:

- Admin xem Subject/Asset canonical.
- Gắn/sửa Actress, Studio, Title, Code, Tag và alias.
- Merge/split/relink có review, không đoán khi ambiguous.
- Catalog transaction cập nhật metadata và phát snapshot/version mới.
- Query cập nhật filter/search; Gallery chỉ đọc read model.

Database/API cụ thể chưa được feature hóa. Không nên code table Actress/Studio/Tag từ manual này trước khi có Brief/Design riêng.

## UC09 — USE Album linking (BUSINESS TARGET)

- Scan tạo ALBUM Subject theo folder.
- Hệ thống có thể đề xuất candidate `FULL_ALBUM_OF` tới Syncdroid VIDEO.
- Người dùng review candidate.
- Approve tạo relation giữa hai Subject, không merge Asset/Subject.
- Query projection cho phép đi từ video sang full album và ngược lại.

Candidate evidence và relation schema vẫn cần feature riêng.

## UC10 — Import/backfill V1 (ROADMAP Phase 7)

```mermaid
flowchart TB
    V1["<font color='white'>V1 database / filesystem<br/>read-only</font>"] --> INVENTORY["<font color='white'>Inventory + normalize</font>"]
    INVENTORY --> DRYRUN["<font color='white'>Dry-run report<br/>valid / duplicate / unresolved</font>"]
    DRYRUN --> REVIEW["<font color='white'>Người dùng duyệt</font>"]
    REVIEW --> APPLY["<font color='white'>Batch idempotent import<br/>checkpoint</font>"]
    APPLY --> CAT["<font color='white'>Catalog V2</font>"]
    CAT --> REBUILD["<font color='white'>Rebuild Query/search</font>"]
    REBUILD --> RECON["<font color='white'>Reconciliation<br/>counts + unresolved</font>"]

    style V1 fill:#009688,stroke:#fff,stroke-width:2px
    style INVENTORY fill:#FF9800,stroke:#fff,stroke-width:2px
    style DRYRUN fill:#4CAF50,stroke:#fff,stroke-width:2px
    style REVIEW fill:#FF9800,stroke:#fff,stroke-width:2px
    style APPLY fill:#2196F3,stroke:#fff,stroke-width:2px
    style CAT fill:#9C27B0,stroke:#fff,stroke-width:2px
    style REBUILD fill:#2196F3,stroke:#fff,stroke-width:2px
    style RECON fill:#4CAF50,stroke:#fff,stroke-width:2px
```

Import không sửa/xóa V1. Thứ tự rollout dự kiến: fixture → pilot root → JOKE video → JOKE assets → USE video/assets → USE Album.

## UC11 — Recovery và rebuild (ĐÃ CÓ một phần)

- Elasticsearch index có thể rebuild từ `query_db` rồi chuyển alias atomically.
- Redis detail cache có thể mất/xóa; request sẽ đọc PostgreSQL và cache lại.
- Query projection đầy đủ từ Catalog cần event replay hoặc rebuild endpoint/snapshot source hoàn chỉnh hơn; đây còn là gap trước cutover lớn.
- DLT cần thao tác xem/replay có kiểm soát, không replay mù.

## UC12 — Observability và performance (ROADMAP Phase 8)

- Correlation ID xuyên HTTP và sau đó Kafka.
- Metrics HTTP/Kafka lag/cache/DB pool/processing duration.
- Trace OpenTelemetry xuyên Gateway → service → Kafka.
- Prometheus/Grafana cho metrics; ELK cho structured logs.
- JFR/JMC, benchmark và load test sau khi có baseline.

Observability không thay đổi business state và lỗi log shipping không được chặn request.

## UC13 — Frontend cutover (DRAFT, sau backend parity)

1. Media Library/Gallery V2 gọi Gateway, không gọi trực tiếp database/search engine.
2. Query DTO map sang V2 view model; không ép backend giả shape V1.
3. Chạy V1 và V2 song song theo URL riêng.
4. Pilot dataset rồi đối chiếu count, filter, card fields, preview/player.
5. Chỉ opt-in/cutover khi backfill, processing và parity gate đạt; V1 vẫn là rollback.

## Khi nào dùng HTTP, khi nào dùng Kafka?

| Tình huống | Cơ chế | Lý do |
| --- | --- | --- |
| UI cần response ngay | HTTP qua Gateway | Request/response trực tiếp |
| Worker cần locator để phát file | HTTP Worker → Catalog | Cần trạng thái hiện tại ngay |
| Scan đã approve file | Kafka event | Catalog xử lý độc lập |
| Catalog thay đổi Subject | Kafka snapshot | Query tự hội tụ |
| Xử lý file nền | Kafka work queue | Không chặn API/UI |
| Operation/admin cần xác nhận ngay | HTTP command | Người dùng cần kết quả rõ |

Kafka không được dùng chỉ để “trông giống microservice”.

## Failure map

| Điểm lỗi | Hành vi |
| --- | --- |
| Parser không hiểu file | Tạo Issue, không đoán |
| Outbox publish lỗi | Row vẫn pending, tăng attempt/error |
| Kafka giao event trùng | Consumer dedupe/no-op |
| Consumer lỗi nhiều lần | Record sang `.DLT` |
| Worker root/path/file lỗi | Retry hữu hạn rồi DLT; không lộ absolute path |
| Elasticsearch lỗi | Query fallback PostgreSQL và báo degraded |
| Redis lỗi | Detail đọc PostgreSQL |
| Import ambiguous | Report unresolved, không tự apply |

## E2E sau FT013 cần chứng minh gì?

Một fixture được scan và approve phải:

1. Tạo đúng một Subject/Asset canonical.
2. Tạo processing request.
3. Được Worker đọc metadata.
4. Cập nhật Catalog đúng một lần về mặt hiệu lực.
5. Hội tụ metadata sang Query.
6. Chạy lại không tạo duplicate Asset hoặc side effect khác.

Thumbnail/GIF/hash, metadata management và import V1 cần E2E riêng khi feature tương ứng được thiết kế.
