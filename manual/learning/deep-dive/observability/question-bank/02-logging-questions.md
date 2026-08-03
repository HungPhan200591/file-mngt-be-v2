# Logging & ELK — Interview Question Bank

Nguồn factual: [Structured Logging & ELK deep-dive](../02-structured-logging-elk.md) · Ôn nhanh: [Core Recall Sheet](../summary/02-structured-logging.md)

## Coverage

| Concept | Rapid chain | Anchor questions | Depth |
| --- | --- | --- | --- |
| Log event và component boundaries | Chain A | `OBS-LOG-001` | Foundation |
| Structured logging, ECS, MDC | Chain B | `OBS-LOG-002` | Foundation → Senior |
| Sync/async, queue, backpressure | Chain C | `OBS-LOG-003` | Senior |
| File shipping, `sincedb`, rotation | Chain D | `OBS-LOG-004`, `005` | Senior |
| Query, incident, storage isolation | Chain E | `OBS-LOG-006`, `007`, `008` | Senior → Architect |

**Tổng coverage:** 5 chains · 37 rapid Q&A · 8 anchor questions (`2 Foundation`, `4 Senior`, `2 Architect`).

## Rapid Question Chains

### Chain A — WHY → WHAT: Tại sao logging tồn tại? (8)

1. **Q: Database đã có state, tại sao cần log?**<br>
   **A:** State cho biết **đang là gì**; log cho biết **đã xảy ra gì theo thời gian** để tái dựng flow.
2. **Q: HTTP error response chưa đủ sao?**<br>
   **A:** Response chỉ mô tả boundary cuối; log giữ **quyết định trung gian + context** bên trong hệ thống.
3. **Q: Đơn vị nhỏ nhất của logging là gì?**<br>
   **A:** Một **log event** gồm time, severity, source, message, context và outcome/exception.
4. **Q: Log có phải source of truth không?**<br>
   **A:** Operational log thường là **observation record**, không phải canonical business state.
5. **Q: Log có tự động là audit trail không?**<br>
   **A:** Không; audit cần **immutability, access control, retention và integrity** rõ hơn.
6. **Q: Metric, log và trace khác nhau thế nào?**<br>
   **A:** Metric cho xu hướng tổng hợp, log cho event chi tiết, trace cho **causal path/span** xuyên boundary.
7. **Q: Một log tốt phải giúp trả lời gì?**<br>
   **A:** **When — where — what — context — outcome**, không chỉ một câu văn mô tả.
8. **Q: Keyword chain của pipeline là gì?**<br>
   **A:** **Event → Context → Schema → Encode → Append → Ship → Index → Query → Retain**.

### Chain B — WHAT → HOW: Structured logging và ECS (7)

1. **Q: Structured logging có đơn giản là log JSON không?**<br>
   **A:** Không; cần **schema ổn định + field có nghĩa + query contract**, JSON chỉ là encoding phổ biến.
2. **Q: Tại sao field tốt hơn nhét mọi thứ vào `message`?**<br>
   **A:** Field cho **exact query, mapping và aggregation**; wildcard trên message dễ chậm và sai.
3. **Q: SLF4J làm gì?**<br>
   **A:** **Facade/API** để source code gọi logging mà không phụ thuộc trực tiếp backend.
4. **Q: Logback làm gì?**<br>
   **A:** **Backend** tạo/dispatch event qua encoder và appender.
5. **Q: Spring Boot ECS làm gì?**<br>
   **A:** Chọn **structured JSON format theo Elastic Common Schema** cho console/file output.
6. **Q: Bật ECS có bỏ Logback hoặc Logstash không?**<br>
   **A:** Không; ECS là format, Logback là backend, Logstash là **shipper stage độc lập**.
7. **Q: MDC dùng cho gì và nguy hiểm ở đâu?**<br>
   **A:** MDC gắn **request context** như `correlationId`; quên cleanup/propagate có thể rò hoặc mất context.

### Chain C — HOW → FAILURE: Appender, sync/async và backpressure (7)

1. **Q: Project hiện tại có cấu hình async logging không?**<br>
   **A:** Không thấy `AsyncAppender`/queue config; chỉ có file ECS output, nên **không được suy ra async**.
2. **Q: OS page cache có biến file appender thành async không?**<br>
   **A:** Không; kernel buffering và **JVM async dispatch** là hai lớp khác nhau.
3. **Q: Logback `AsyncAppender` hoạt động thế nào?**<br>
   **A:** Caller enqueue event vào **BlockingQueue**, worker thread dispatch tới child appender.
4. **Q: Nó có dùng RingBuffer không?**<br>
   **A:** `AsyncAppender` chuẩn của Logback dùng **BlockingQueue**; RingBuffer thường gắn với Log4j2 Disruptor.
5. **Q: Queue gần đầy có thể xảy ra gì?**<br>
   **A:** Default có thể **discard TRACE/DEBUG/INFO** khi còn ít capacity để ưu tiên WARN/ERROR.
6. **Q: Queue đầy có luôn drop để không block không?**<br>
   **A:** Không; `neverBlock=false` là default nên caller có thể block. `true` đổi sang **drop-on-full**.
7. **Q: Trade-off async cốt lõi?**<br>
   **A:** Giảm caller latency nhưng thêm **heap queue, drop/block policy và shutdown flush risk**.

### Chain D — FAILURE → GUARANTEE: File shipping và `sincedb` (8)

1. **Q: Vì sao project chọn app → file → Logstash?**<br>
   **A:** Để **tách network collector** khỏi application và dùng file local làm buffer.
2. **Q: Logstash down thì app luôn vô ảnh hưởng?**<br>
   **A:** Không; app tránh network failure trực tiếp nhưng **disk/backlog** vẫn là coupling gián tiếp.
3. **Q: `sincedb` lưu gì?**<br>
   **A:** **Checkpoint identity/vị trí đọc** để file input resume sau restart.
4. **Q: `start_position=beginning` có ép đọc lại mọi file mỗi restart không?**<br>
   **A:** Không; nó chủ yếu áp dụng file chưa từng thấy, còn file đã biết theo **sincedb checkpoint**.
5. **Q: `sincedb` có đảm bảo exactly-once không?**<br>
   **A:** Không; checkpoint không phải transaction atomically coupled với Elasticsearch indexing.
6. **Q: Duplicate xuất hiện lúc nào?**<br>
   **A:** Event đã index nhưng crash trước checkpoint có thể bị **re-read/re-index**.
7. **Q: Loss xuất hiện lúc nào?**<br>
   **A:** File rotate/xóa trước khi tail, buffer chưa flush hoặc disk lỗi có thể làm **event biến mất**.
8. **Q: Rotation và retention cần cân bằng gì?**<br>
   **A:** Local retention phải chứa được **worst-case outage backlog**, không chỉ traffic ngày thường.

### Chain E — PROJECT → EVOLUTION: Query và kiến trúc storage (7)

1. **Q: Tại sao query `correlationId` tốt hơn wildcard message?**<br>
   **A:** Structured field cho **exact filtering** và schema ổn định.
2. **Q: Có nên biến mọi ID thành field không?**<br>
   **A:** Field search được có thể hữu ích, nhưng phải quản trị **mapping/cardinality/security** và tránh dùng bừa cho aggregation.
3. **Q: Tại sao logs data stream tách media search index?**<br>
   **A:** Khác **mapping, lifecycle, ownership và workload shape**.
4. **Q: Tách index đã tạo resource isolation chưa?**<br>
   **A:** Chưa; cùng Elasticsearch instance vẫn tranh **CPU, heap, disk và I/O**.
5. **Q: Logstash khác Filebeat/Fluent Bit ở đâu?**<br>
   **A:** Logstash mạnh về **transform/filter/routing**; edge shipper thường nhẹ hơn.
6. **Q: Khi nào operational log không đủ?**<br>
   **A:** Khi cần **audit durable, trace causal spans hoặc business source of truth**.
7. **Q: Production hóa cần chốt thêm gì?**<br>
   **A:** Async policy, rotation/backlog budget, lifecycle, redaction, access control, SLO shipper và capacity isolation.

## Anchor Interview Questions

### OBS-LOG-001 — `FOUNDATION` · `COMMON_CORE`

**Question:** Phân biệt SLF4J, Logback, Spring Boot ECS, Logstash, Elasticsearch và Kibana.

**Interviewer evaluates:** Có mental model đúng về ranh giới component hay chỉ nhớ tên tool.

**Trả lời 30 giây:** SLF4J là API source gọi; Logback là backend dispatch event; Spring Boot ECS encode event thành JSON schema; Logstash tail/transform/ship; Elasticsearch index/search; Kibana là UI query. ECS không thay backend hay shipper.

**Answer spine:** log event → facade/backend → encoder/appender → shipper → storage/UI.

**Project evidence:** `apps/*/application.yml`, `infra/observability/logstash/pipeline/file-mngt-v2.conf`.

**Trade-offs:** Mỗi stage thay được nhưng đổi format, failure semantics và vận hành.

**Follow-up ladder:** Structured logging khác JSON? MDC nằm ở stage nào? Nếu bỏ Logstash thì thay bằng gì?

**Red flags:** Gọi Elasticsearch là canonical DB; nói ECS thay Logback/Logstash.

### OBS-LOG-002 — `FOUNDATION` · `COMMON_CORE`

**Question:** Structured logging giải quyết vấn đề gì và ECS đóng vai trò nào?

**Interviewer evaluates:** Hiểu schema/field hay chỉ thích JSON.

**Trả lời 30 giây:** Structured logging biến dữ liệu quan trọng thành field ổn định để query/mapping thay vì regex message. ECS cung cấp naming convention chung; Spring Boot 4 có thể encode ECS built-in và đưa MDC/key-value vào JSON.

**Answer spine:** text ambiguity → schema → exact query → shared convention → project config.

**Project evidence:** `logging.structured.format.file: ecs`, `spring.application.name`.

**Trade-offs:** Schema governance, mapping/cardinality, readability và storage overhead.

**Follow-up ladder:** Khi nào vẫn dùng `message`? Custom field thêm bằng MDC hay fluent API? Field nào cần redact?

**Red flags:** “Mọi JSON đều structured tốt”; parse text bằng Grok dù app kiểm soát schema.

### OBS-LOG-003 — `SENIOR` · `COMMON_SCENARIO`

**Question:** Project đang synchronous hay asynchronous logging? Nếu thêm `AsyncAppender`, backpressure hoạt động ra sao?

**Interviewer evaluates:** Phân biệt evidence với suy đoán và hiểu concurrency/failure semantics.

**Trả lời 30 giây:** Project chưa cấu hình `AsyncAppender`, nên không thể nói caller chỉ enqueue. Nếu thêm Logback `AsyncAppender`, event đi qua BlockingQueue; gần đầy có thể drop level thấp, đầy có thể block vì `neverBlock=false` mặc định hoặc drop nếu bật `true`.

**Answer spine:** inspect config → current semantics → optional queue → near-full/full → shutdown flush.

**Project evidence:** Không có `logback-spring.xml`, `AsyncAppender`, `neverBlock` hay queue setting trong source.

**Trade-offs:** Caller latency vs event loss/blocking, heap, caller data và shutdown time.

**Follow-up ladder:** RingBuffer thuộc cơ chế nào? Queue size chọn ra sao? Làm sao đo dropped events?

**Red flags:** “OS cache = async”; “Logback dùng RingBuffer”; “neverBlock mặc định true”.

### OBS-LOG-004 — `SENIOR` · `PROJECT_APPLICATION`

**Question:** Tại sao Backend V2 dùng decoupled file shipping và nó chưa giải quyết failure nào?

**Interviewer evaluates:** Có nhìn đủ network, disk, backlog và business isolation.

**Trả lời 30 giây:** File shipping tránh app gửi log trực tiếp tới collector và cho phép Logstash tail độc lập. Nó giảm network coupling nhưng không xóa disk latency/full, rotation outrun, backlog overflow hoặc caller impact của synchronous appender.

**Answer spine:** network dependency → local buffer → independent shipper → indirect disk coupling → capacity guardrails.

**Project evidence:** app ghi `*.json.log`; Compose mount read-only; Logstash file input.

**Trade-offs:** Đơn giản và recoverable backlog đổi lấy disk capacity, host lifecycle và checkpoint management.

**Follow-up ladder:** Container ephemeral thì sao? Network appender khi nào hợp lý? Backlog SLO đo thế nào?

**Red flags:** “Logstash down thì REST chắc chắn luôn 200”; “ghi file không thể block”.

### OBS-LOG-005 — `SENIOR` · `COMMON_SCENARIO`

**Question:** `sincedb`, rotation và Elasticsearch retention phối hợp thế nào? Có exactly-once không?

**Interviewer evaluates:** Hiểu ba lifecycle khác nhau và delivery edge cases.

**Trả lời 30 giây:** Rolling policy quản file local; `sincedb` checkpoint vị trí Logstash đọc; Elasticsearch lifecycle quản document searchable. Ba cơ chế độc lập và không tạo exactly-once: crash/checkpoint có thể duplicate, rotate/delete quá sớm có thể loss.

**Answer spine:** local lifecycle → read checkpoint → storage lifecycle → duplicate/loss windows → backlog budget.

**Project evidence:** Có explicit `sincedb_path`; chưa source-control explicit rolling limits hay retention production.

**Trade-offs:** Disk budget, recovery window, duplicate tolerance, retention cost và compliance.

**Follow-up ladder:** `start_position` tác động file cũ thế nào? Mất sincedb ra sao? Deduplicate bằng document ID có đáng không?

**Red flags:** “sincedb xác nhận Elasticsearch commit”; gán cứng 7/30 ngày khi config không có.

### OBS-LOG-006 — `SENIOR` · `COMMON_SCENARIO`

**Question:** Bạn điều tra một request lỗi xuyên Gateway và Scan bằng structured logging thế nào?

**Interviewer evaluates:** Khả năng biến schema/context thành incident workflow.

**Trả lời 30 giây:** Bắt đầu bằng `correlationId`, lọc time range và service, sắp theo timestamp, xem level/logger/outcome rồi nối sang event/business key có cấu trúc. Nếu phải wildcard message liên tục, tôi coi đó là schema gap cần sửa.

**Answer spine:** scope time → correlation → service/source → outcome/exception → business/event key → identify missing fields.

**Project evidence:** Gateway canonicalize `X-Correlation-Id`; downstream đưa vào MDC/ECS.

**Trade-offs:** Context phong phú giúp debug nhưng tăng volume, mapping và nguy cơ lộ dữ liệu.

**Follow-up ladder:** Async boundary mất MDC xử lý sao? Correlation ID khác trace ID? Clock skew ảnh hưởng ordering?

**Red flags:** Chỉ grep message; log raw path/secret để “debug nhanh”.

### OBS-LOG-007 — `ARCHITECT` · `ARCHITECTURE_EVOLUTION`

**Question:** Vì sao logs data stream và media search index phải tách, và giới hạn của việc tách trên cùng cluster là gì?

**Interviewer evaluates:** Phân biệt logical isolation với resource isolation.

**Trả lời 30 giây:** Tách để mỗi workload có mapping, lifecycle, ownership và query pattern riêng. Nhưng cùng Elasticsearch cluster vẫn chia CPU/heap/disk/I/O; muốn bảo vệ business search trước log spike cần quota/capacity/tier hoặc cluster isolation, không chỉ tên index khác.

**Answer spine:** workload shape → mapping/lifecycle → ownership → shared resource contention → evolution options.

**Project evidence:** data stream `logs-file_mngt_v2-local` và index `media-subject-search` cùng Elasticsearch local.

**Trade-offs:** Một cluster rẻ/dễ học; nhiều cluster tăng isolation nhưng tăng chi phí vận hành.

**Follow-up ladder:** Shard/tier/quota nào giúp? Khi nào tách cluster? Loki thay đổi cost model ra sao?

**Red flags:** “Tách index nên log spike không thể ảnh hưởng media search”.

### OBS-LOG-008 — `ARCHITECT` · `ARCHITECTURE_EVOLUTION`

**Question:** Bạn sẽ production hóa pipeline logging hiện tại theo thứ tự nào?

**Interviewer evaluates:** Biết ưu tiên reliability/security/cost thay vì thêm tool theo phong trào.

**Trả lời 30 giây:** Tôi chốt schema/redaction trước, đo volume và failure budget, đặt rolling/backlog/lifecycle, monitor shipper/disk/indexing rồi mới cân nhắc async, edge shipper hay cluster isolation. Guarantee phải được gọi đúng là best effort hay audit-grade.

**Answer spine:** data contract → security → capacity → retention → pipeline SLO → async/shipper → isolation → game day.

**Project evidence:** Baseline local đã có ECS/Logstash/Elasticsearch/Kibana; các policy production chưa thuộc FT014.

**Trade-offs:** Reliability và search depth tăng kéo theo storage, operational complexity và data governance.

**Follow-up ladder:** SLO nào cho logging? Test outage ra sao? Audit event nên đi pipeline nào? Sampling log có hợp lý không?

**Red flags:** Bật `neverBlock=true` cho mọi nơi; giữ mọi log mãi; dùng operational log làm audit source of truth.

## Retrieval Practice — không nhìn đáp án phía trên

1. Vẽ lại pipeline bằng 8 box và nói owner của từng box.
2. Giải thích trong 20 giây vì sao ECS không thay Logback.
3. Nêu ba điểm khác nhau giữa OS page cache và `AsyncAppender`.
4. Queue async gần đầy/đầy/shutdown có ba failure mode nào?
5. Dùng một timeline minh họa duplicate do index trước checkpoint.
6. Dùng một timeline minh họa loss do rotation outrun shipper.
7. Tại sao `start_position=beginning` không có nghĩa restart là đọc lại tất cả?
8. Nêu ba lifecycle độc lập: local file, checkpoint, Elasticsearch document.
9. Khi nào wildcard `message` chỉ ra schema gap?
10. Tách index nhưng cùng cluster còn chia sẻ tài nguyên nào?
11. Operational logging thiếu thuộc tính gì để làm audit trail?
12. Đưa ra quyết định sync/async cho một API latency-sensitive và bảo vệ trade-off đó.

**Keyword cứu hộ:** `boundary · schema · context · BlockingQueue · backpressure · checkpoint · best effort · backlog · logical isolation`.
