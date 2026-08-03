# ❓ Logging & ELK Stack — Interview Question Bank

Bộ câu hỏi phỏng vấn Chuyên sâu (Senior / Lead) về Structured Logging, Elastic Common Schema (ECS), Logstash Ingestion Pipeline và Kibana Discovery.

---

## 📊 Bảng Ma trận Coverage

| Level | Foundation | Senior | Architect | Tổng số câu |
| :--- | :---: | :---: | :---: | :---: |
| **Số lượng** | 1 | 5 | 1 | 7 |

---

## 🎯 Danh sách Câu hỏi Chi tiết & Đáp án Chuẩn

### OBS-LOG-000 — `FOUNDATION`
**Question:** Phân biệt vai trò của SLF4J, Logback, Spring Boot 4 ECS Formatter, Logstash, Elasticsearch và Kibana trong dây chuyền Logging? Khai báo `logging.structured.format.file=ecs` có phải là bỏ Logback và Logstash không?<br>
**Target depth:** `D1` · **Interview likelihood:** `HIGH` · **Question type:** `COMMON_CORE`<br>
**Interviewer evaluates:** Hiểu biết nền tảng về bức tranh toàn cảnh của hệ thống Logging (bản chất từng thành phần và dây chuyền hoạt động).<br>

⚡ **Trả lời siêu ngắn (Elevator Pitch)**:
> *"**SLF4J** (Code Interface) ➔ **Logback** (Engine ghi đĩa) ➔ **Spring Boot ECS** (Formatter định dạng JSON) ➔ **Logstash** (Container thu gom đọc file ngầm) ➔ **Elasticsearch** (DB lưu trữ search) ➔ **Kibana** (Giao diện Web UI). Khai báo Spring Boot 4 ECS **KHÔNG bỏ Logback hay Logstash**, mà chỉ bảo Logback xuất ra JSON chuẩn ECS để Logstash đọc ngay mà không cần cài thêm plugin ngoài."*

🧠 **Chuỗi Hỏi - Đáp Keyword (Memory Flashcard Chain)**:
- ❓ **SLF4J làm nhiệm vụ gì?** ➔ 💡 **Interface chuẩn Java để code gọi `log.info()`**.
- ❓ **Logback nằm ở đâu và làm gì?** ➔ 💡 **Engine chạy ngầm trong JVM App để thực sự ghi log ra file/console**.
- ❓ **Spring Boot 4 ECS Formatter giúp ích gì?** ➔ 💡 **Định dạng câu log thành JSON chuẩn ECS mà KHÔNG cần thêm file XML hay thư viện ngoài**.
- ❓ **Logstash đứng ở đâu và làm gì?** ➔ 💡 **Container hạ tầng đứng NGOÀI app, chủ động đọc file `.json` ngầm để đẩy về Elasticsearch**.
- ❓ **Elasticsearch và Kibana khác nhau thế nào?** ➔ 💡 **Elasticsearch là DB lưu trữ & index log; Kibana là Màn hình Web UI để kỹ sư gõ search**.
- 🔑 **Keyword cốt lõi cần nhớ**: **SLF4J (Interface) ➔ Logback (Engine) ➔ Spring Boot ECS (Formatter) ➔ Logstash (Shipper) ➔ Elasticsearch (Storage DB) ➔ Kibana (Web UI)**.

**Answer outline:**
- **Dây chuyền 6 bước từ Code đến Màn hình Web**:
  1. Lập trình viên gọi `LOGGER.info(...)` qua **SLF4J Interface**.
  2. **Logback Engine** tiếp nhận event, kết hợp với **Spring Boot 4 ECS Formatter** để mã hóa câu log thành JSON chuẩn Elastic Common Schema.
  3. Logback ghi bất đồng bộ câu log JSON xuống đĩa local (`logs/scan-service.json`).
  4. Container **Logstash** đọc ngầm file JSON theo cơ chế File Tail (`sincedb`).
  5. Logstash ship dữ liệu về **Elasticsearch Cluster** để đánh chỉ mục (Indexing).
  6. Kỹ sư vận hành mở **Kibana Web UI** (`http://localhost:18114`) gõ KQL để tìm kiếm log.
- **Giải ngộ nhận**: Spring Boot 4 ECS không thay thế Logback hay Logstash. Nó là tính năng mới giúp loại bỏ hoàn toàn các file cấu hình `logback-spring.xml` rườm rà và không cần cài thêm library `logstash-logback-encoder` ngoài.<br>
**Required trade-offs:** Cần Spring Boot 3.4+ / 4.0+ để hỗ trợ out-of-the-box ECS structured logging.<br>
**Follow-up ladder:** Tại sao console log vẫn giữ định dạng ANSI text trong khi file log định dạng ECS JSON?<br>
**Red flags:** Trả lời "Dùng Spring Boot 4 ECS thì gỡ bỏ Logback và Logstash".

---

### OBS-LOG-000B — `SENIOR`
**Question:** Phân tích rủi ro khi copy-paste code mà quên đổi `TargetClass.class` trong khai báo Logger thủ công? Đánh đổi (Trade-offs) của kiến trúc Zero-Lombok và tại sao tạo `BaseService` chứa Logger lại là Anti-pattern?<br>
**Target depth:** `D2` · **Interview likelihood:** `HIGH` · **Question type:** `COMMON_SCENARIO`<br>
**Interviewer evaluates:** Tư duy về Clean Code, rủi ro chẩn đoán sai log trên Kibana, trade-offs của Lombok vs Modern Java 25, và nguyên tắc Composition over Inheritance.<br>

⚡ **Trả lời siêu ngắn (Elevator Pitch)**:
> *"Quên đổi `TargetClass.class` sẽ làm **Kibana in sai trường `log.logger`**, gây **chẩn đoán sai lệch nghiêm trọng** khi troubleshoot. Chọn Zero-Lombok để **tương thích Java 25 Native tuyệt đối** mà không sợ nổ compiler hack. Tạo `BaseService` chứa logger là **Anti-pattern** vì vi phạm Composition over Inheritance và giảm hiệu năng runtime `getClass()`."*

🧠 **Chuỗi Hỏi - Đáp Keyword (Memory Flashcard Chain)**:
- ❓ **Hậu quả lớn nhất khi quên đổi `TargetClass.class`?** ➔ 💡 **Kibana in sai `log.logger` ➔ Search log theo class mới KHÔNG THẤY ➔ Chẩn đoán nhầm bug sang service khác**.
- ❓ **Trade-offs của kiến trúc Zero-Lombok?** ➔ 💡 **Chấp nhận khai báo thủ công 1 dòng Logger ở mỗi class ➔ Đổi lại tương thích 100% Java 25 Native không sợ nổ compiler plugin**.
- ❓ **Tại sao tạo BaseClass chứa Logger lại là Anti-pattern?** ➔ 💡 **Vi phạm Composition over Inheritance + Tốn chi phí runtime `getClass()` thay vì static compile-time**.
- 🔑 **Keyword cốt lõi cần nhớ**: **Wrong `log.logger` (Misleading Root Cause) ➔ Zero-Lombok (Java 25 Native Safety) ➔ No Base Logger (OOD Standard)**.

**Answer outline:**
- **Rủi ro Quên đổi Class Name**: Trường `log.logger` trong JSON Log bị mang tên class cũ. Khi sự cố xảy ra trên Prod, Kỹ sư search theo tên class mới trên Kibana sẽ bị "mù thông tin" hoặc đoán nhầm nguyên nhân.
- **Trade-offs Zero-Lombok**:
  - *Không dùng Lombok*: Phải gõ thủ công `private static final Logger LOGGER = ...` và constructor.
  - *Lợi ích*: Loại bỏ "compiler hack" của Lombok, codebase hoàn toàn dùng Java 25 `record` native, build cực kỳ mượt mà với mọi IDE/Spotless Formatter.
- **Anti-pattern BaseService Logger**: Tạo class cha `BaseService` chứa `logger = LoggerFactory.getLogger(getClass())` làm dính chặt kế thừa không cần thiết và tốn runtime cost của `getClass()`. Dòng static final logger ở từng class mới là chuẩn mực OOD.<br>
**Required trade-offs:** Đỏi hỏi tính cẩn thận của Developer khi copy-paste code thủ công.<br>
**Follow-up ladder:** Công cụ static analysis nào giúp tự động cảnh báo nếu `LoggerFactory.getLogger(...)` nhầm class name?<br>
**Red flags:** Tạo `BaseService` chỉ để dùng chung 1 biến logger.

---

### OBS-LOG-001 — `SENIOR`
**Question:** Tại sao Logging trong dự án lại dùng cơ chế Ghi File ECS JSON local + Logstash Ship (Decoupled File Shipping) thay vì cho Microservice trực tiếp đẩy Log qua Network (Socket/HTTP Appender) về Logstash/Elasticsearch?<br>
**Target depth:** `D2-D3` · **Interview likelihood:** `HIGH` · **Question type:** `COMMON_SCENARIO`<br>
**Interviewer evaluates:** Hiểu biết về rủi ro Cascading Failure, hiệu năng I/O của Operating System và nguyên tắc Decoupling hạ tầng logging.<br>

⚡ **Trả lời siêu ngắn (Elevator Pitch)**:
> *"Dùng **Decoupled File Shipping** (ghi file local qua OS Page Cache < 1ms, Logstash tail ngầm) để **ngăn ngừa Cascading Failure** (tránh sập mạng/Logstash kéo sập REST API) và tối ưu **hiệu năng ghi đĩa hạt nhân (Kernel OS Buffered Writes)**."*

🧠 **Chuỗi Hỏi - Đáp Keyword (Memory Flashcard Chain)**:
- ❓ **Tại sao không đẩy log trực tiếp qua Network Socket/HTTP?** ➔ 💡 **Nguy cơ sập dây chuyền (Cascading Failure)** làm ngắt REST API khi Logstash bị chậm/sập.
- ❓ **Tại sao ghi file local lại siêu nhanh?** ➔ 💡 **OS Page Cache Buffer trong RAM Kernel** với thời gian ghi tiệm cận < 1ms.
- ❓ **Logstash sập thì dữ liệu log bị mất không?** ➔ 💡 **Không! Log tích lũy trên đĩa local, Logstash sống lại sẽ đọc từ Offset cũ (`sincedb`)**.
- 🔑 **Keyword cốt lõi cần nhớ**: **Decoupled File Shipping ➔ Chống Cascading Failure + OS Page Cache Fast Write + Logstash Offset Buffer**.

**Answer outline:**
- **Phòng tránh Sập Dây Chuyền (Cascading Failure)**: Nếu Logstash/Elasticsearch gặp sự cố (High CPU, ngắt kết nối mạng, nghẽn I/O, hoặc Disk Full), việc ứng dụng gửi log đồng bộ/bất đồng bộ qua Network Appender sẽ nhanh chóng làm tràn bộ nhớ đệm (Buffer Overflow), tắc nghẽn Worker Thread Pool và làm **treo toàn bộ API nghiệp vụ**.
- **Hiệu năng OS Page Cache cực cao**: Thao tác ghi log ra đĩa local (`/logs/*.json`) sử dụng OS Buffered Writes trong RAM Kernel, thời gian phản hồi ở mức microsecond (µs), tiệm cận bằng 0ms.
- **Tính Độc lập (Decoupled Architecture)**: Container Logstash chạy hoàn toàn tách biệt, chủ động tail file và ship log ngầm theo nhịp độ của nó. Nếu Logstash sập, log chỉ tạm tích lũy trên ổ đĩa local. Khi Logstash sống lại, nó tiếp tục đọc từ vị trí cũ (Offset) mà không mất mát dữ liệu và không ảnh hưởng đến ứng dụng.<br>
**Required trade-offs:** Cần cơ chế xoay vòng file (Log Rotation) để không làm đầy đĩa cứng local.<br>
**Follow-up ladder:** Filebeat khác Logstash ở điểm nào? Khi nào nên dùng Filebeat làm log shipper ở edge layer?<br>
**Red flags:** Cho rằng "Ghi file chậm hơn gửi qua Network Socket".

---

### OBS-LOG-002 — `FOUNDATION`
**Question:** Spring Boot 4 Built-in Structured Logging chuẩn Elastic Common Schema (ECS) hoạt động như thế nào và có ưu điểm gì so với cấu hình Logback/Log4j2 XML truyền thống?<br>
**Target depth:** `D1-D2` · **Interview likelihood:** `HIGH` · **Question type:** `COMMON_CORE`<br>
**Interviewer evaluates:** Cập nhật kiến thức mới của Spring Boot Framework và Elastic Common Schema (ECS) standard.<br>

⚡ **Trả lời siêu ngắn (Elevator Pitch)**:
> *"Spring Boot 4 hỗ trợ tích hợp sẵn `logging.structured.format.file=ecs` giúp **chuẩn hóa 100% các trường JSON log** (`@timestamp`, `service.name`, `correlationId`) theo tiêu chuẩn Elastic Common Schema mà không cần thư viện ngoài, giúp **Elasticsearch ingest & parse siêu tốc** không cần Grok Regex."*

🧠 **Chuỗi Hỏi - Đáp Keyword (Memory Flashcard Chain)**:
- ❓ **Cấu hình Spring Boot 4 ECS JSON log thế nào?** ➔ 💡 **`logging.structured.format.file=ecs` trong application.properties**.
- ❓ **Lợi ích lớn nhất của Elastic Common Schema (ECS)?** ➔ 💡 **Thống nhất tên trường JSON trên toàn hệ thống** (`service.name`, `correlationId`, `log.level`).
- ❓ **Logstash/Elasticsearch hưởng lợi gì từ ECS?** ➔ 💡 **Elasticsearch auto map data type chính xác, Logstash dùng JSON Codec không cần Grok Regex tốn CPU**.
- 🔑 **Keyword cốt lõi cần nhớ**: **Spring Boot 4 Built-in ECS ➔ Structured JSON Log ➔ Auto Mapping ➔ No Grok Regex Required**.

**Answer outline:**
- **Spring Boot 4 Built-in Feature**: Chỉ cần khai báo `logging.structured.format.file=ecs` trong `application.properties`, Spring Boot tự động định dạng mọi log output ra dạng JSON chuẩn ECS mà không cần thêm phụ thuộc thư viện logstash-logback-encoder ngoài.
- **Chuẩn hóa Elastic Common Schema (ECS)**:
  - Tất cả các trường cơ bản được đặt tên thống nhất trên mọi microservice: `@timestamp`, `log.level`, `service.name`, `process.thread.name`, `log.logger`, `message`, `correlationId`.
  - Giúp Elasticsearch tự động map đúng Data Types (keyword, date, text) mà không cần viết custom Grok filter phức tạp ở Logstash.
- **So với Logback XML cũ**: Không còn tình trạng mỗi lập trình viên tự format log text một kiểu (`2026-08-03 INFO [scan-service] [main] ...`), giúp việc query và parse trên Kibana đạt hiệu năng cao nhất.<br>
**Required trade-offs:** Log định dạng JSON đọc bằng mắt thường trên console hơi rối hơn text truyền thống (do đó console vẫn giữ định dạng ansi text, chỉ file mới format ECS JSON).<br>
**Follow-up ladder:** Làm thế nào để thêm custom key-value vào cấu trúc ECS JSON log trong Java?<br>
**Red flags:** Tự viết regex/grok filter để parse log text không cấu hình.

---

### OBS-LOG-003 — `ARCHITECT`
**Question:** Tại sao dữ liệu Log Data Stream (`logs-file_mngt_v2-*`) và Dữ liệu Tìm kiếm Media (`media-subject-search`) trong Elasticsearch phải được phân tách thành 2 Index hoàn toàn độc lập?<br>
**Target depth:** `D3-D4` · **Interview likelihood:** `HIGH` · **Question type:** `PROJECT_APPLICATION`<br>
**Interviewer evaluates:** Tư duy thiết kế Elasticsearch Data Architecture, cách phân tách Workload (Log Ingestion vs Search Query).<br>

⚡ **Trả lời siêu ngắn (Elevator Pitch)**:
> *"Phân tách 2 Index độc lập để **cách ly tải (Workload Isolation)**: Log là Append-Only Write-Heavy (tự xóa sau 7-30 ngày), còn Media Search là Read-Heavy (lưu vĩnh viễn). Tránh việc bùng nổ log làm nghẽn tính năng tìm kiếm của người dùng end-user."*

🧠 **Chuỗi Hỏi - Đáp Keyword (Memory Flashcard Chain)**:
- ❓ **Tại sao phải phân tách Log Index và Business Search Index?** ➔ 💡 **Phân tách đặc tính Workload** (Write-Heavy vs Read-Heavy).
- ❓ **Đặc điểm của Log Data Stream Index?** ➔ 💡 **Append-Only, ghi liên tục, áp dụng Index Lifecycle (ILM) tự xóa sau 7-30 ngày**.
- ❓ **Nếu không phân tách thì hậu quả là gì?** ➔ 💡 **Lượng log spike làm tràn đĩa/CPU Elasticsearch ➔ Nghẽn API tìm kiếm media của User**.
- 🔑 **Keyword cốt lõi cần nhớ**: **Workload Isolation ➔ Log Stream (Append-Only / Short TTL) vs Business Search (Read-Heavy / Permanent)**.

**Answer outline:**
1. **Phân tách Đặc tính Workload (Workload Isolation)**:
   - **Log Data Stream**: Là dữ liệu Append-Only ghi liên tục theo thời gian, tần suất ghi (Write Heavy) cực cao, hiếm khi update hay delete. Phù hợp sử dụng **Elasticsearch Data Streams** với lifecycle tự động (Rollover/Delete old logs).
   - **Media Search Index**: Là dữ liệu nghiệp vụ (Domain Data) phục vụ người dùng tìm kiếm (`query-service`), tần suất đọc (Read Heavy) cao, có thao tác update document khi thông tin media thay đổi.
2. **Bảo vệ Tính Sẵn sàng & Hiệu năng Nghiệp vụ**:
   - Nếu nghẽn log hoặc lượng log bùng nổ (Log Spike), đĩa hay tài nguyên của Log Data Stream bị ảnh hưởng nhưng **Index tìm kiếm Media vẫn phản hồi nhanh chóng cho người dùng end-user**.
3. **Cấu hình Retention Policy Khác nhau**:
   - Log chỉ cần giữ 7 - 30 ngày (tự động xóa qua ILM - Index Lifecycle Management).
   - Media Search Index cần lưu trữ vĩnh viễn theo cơ sở dữ liệu Postgres `catalog_db`.<br>
**Required trade-offs:** Cần quản lý 2 Index Pattern riêng biệt trên Elasticsearch Cluster.<br>
**Follow-up ladder:** Index Lifecycle Management (ILM) trong Elasticsearch gồm những phase nào?<br>
**Red flags:** Lưu chung Log record và Business Search Entity vào cùng một Elasticsearch Index.

---

### OBS-LOG-004 — `SENIOR`
**Question:** Ghi log xuống file local chạy lâu dài trên Production thì xử lý thế nào để không làm tràn ổ đĩa (Disk Full)? Cơ chế Rolling Policy và Retention Policy hoạt động ra sao?<br>
**Target depth:** `D2-D3` · **Interview likelihood:** `HIGH` · **Question type:** `COMMON_SCENARIO`<br>
**Interviewer evaluates:** Hiểu biết về quản lý tài nguyên đĩa cứng trong Logging Framework (Logback/Log4j2), Rolling Policy và sự phối hợp với LogShipper (Logstash Inode Pointer).<br>

⚡ **Trả lời siêu ngắn (Elevator Pitch)**:
> *"File log **không bao giờ bị phình to vô hạn** vì Logback quản lý bằng **Rolling Policy** (tự đóng file cũ, nén `.json.gz` khi đủ 10MB/100MB hoặc theo ngày) và **Retention Policy** (`maxHistory` tự xóa file cũ quá 7-30 ngày, `totalSizeCap` giới hạn tổng GB thư mục log). Logstash chỉ cần đọc ngầm qua pointer `sincedb` để ship lên Elasticsearch rồi file nén local tự động xóa an toàn."*

🧠 **Chuỗi Hỏi - Đáp Keyword (Memory Flashcard Chain)**:
- ❓ **Ghi log file lâu dài có bị phình to vô hạn không?** ➔ 💡 **Không! Logback tự động Rolling & Clear file cũ**.
- ❓ **Log Rolling Policy chia file dựa trên cái gì?** ➔ 💡 **Kích thước (Size-based: 10MB/100MB) và Thời gian (Time-based: Hằng ngày)**.
- ❓ **Cơ chế dọn dẹp file log cũ làm việc thế nào?** ➔ 💡 **`maxHistory` (xóa file cũ quá 7-30 ngày) + `totalSizeCap` (hạn ngạch tổng dung lượng)**.
- ❓ **Xóa file log nén local có làm mất log trên Kibana không?** ➔ 💡 **Không! Logstash đã ship dữ liệu lên Elasticsearch rồi**.
- 🔑 **Keyword cốt lõi cần nhớ**: **Size/Time Rolling (.json.gz) + maxHistory (Retention) + Logstash sincedb Pointer = Safety**.

**Answer outline:**
- **Size & Time-based Rolling**: Logback tự động đóng file log hiện tại, nén thành file GZIP (ví dụ `catalog-service-2026-08-03.1.json.gz`) và tạo file `.json` trống mới.
- **Log Retention Policy**:
  - `maxHistory` (ví dụ 7 - 30 ngày): Tự động quét và xóa các file nén cũ quá số ngày quy định.
  - `totalSizeCap` (ví dụ 3GB): Giới hạn dung lượng toàn thư mục logs. Vượt quá ngưỡng này Logback tự xóa file cũ nhất.
- **Con trỏ `sincedb` của Logstash**: Logstash lưu con trỏ đọc inode đĩa. Ngay khi Logstash đọc xong và Elasticsearch index thành công, log đã an toàn trên ELK Cluster. File local chỉ là đệm tạm thời.<br>
**Required trade-offs:** Đảm bảo đủ khoảng trống dung lượng đĩa tối thiểu cho `totalSizeCap` để tránh nổ Disk Full trong ngày cao điểm spikes traffic.<br>
**Follow-up ladder:** Nếu Logstash chưa kịp ship log mà file log đã bị xoay vòng xóa mất thì xử lý thế nào?<br>
**Red flags:** Không cấu hình `maxHistory` hoặc `totalSizeCap` trong file Logback configuration.

---

### OBS-LOG-005 — `SENIOR`
**Question:** Việc ghi log ngầm bất đồng bộ (Asynchronous Logging) trong Spring Boot vận hành thế nào? Thread nào chạy, cơ chế RingBuffer và cách hệ thống tự vệ khi tràn đệm (Buffer Overflow) ra sao?<br>
**Target depth:** `D2-D3` · **Interview likelihood:** `HIGH` · **Question type:** `COMMON_CORE`<br>
**Interviewer evaluates:** Hiểu biết sâu sắc về Concurrency Architecture trong Logging, AsyncAppender, RingBuffer (LMAX Disruptor) và cơ chế phòng vệ Discarding Threshold.<br>

⚡ **Trả lời siêu ngắn (Elevator Pitch)**:
> *"Khi gọi `LOGGER.info()`, **Tomcat Worker Thread** chỉ ném `LogEvent` vào bộ nhớ RAM **RingBuffer** (< 0.1ms) rồi quay lại phục vụ API ngay. Một **AsyncAppender Daemon Thread** ngầm riêng biệt sẽ rút log theo lô (Batch) để ghi xuống đĩa OS Page Cache. Khi đệm đầy 80%, hệ thống tự động **drop (bỏ qua)** log TRACE/DEBUG/INFO để nhường chỗ cho log ERROR, tuyệt đối không gây nghẽn API."*

🧠 **Chuỗi Hỏi - Đáp Keyword (Memory Flashcard Chain)**:
- ❓ **Tomcat Worker Thread có trực tiếp ghi đĩa không?** ➔ 💡 **Không! Chỉ ném LogEvent vào RingBuffer RAM trong < 0.1ms**.
- ❓ **Thread nào thực hiện công việc ghi đĩa ngầm?** ➔ 💡 **AsyncAppender-Worker Daemon Thread** (Chạy bất đồng bộ ngầm).
- ❓ **Khi bộ nhớ đệm RingBuffer bị đầy 80% thì sao?** ➔ 💡 **`discardingThreshold` tự động drop log TRACE/DEBUG/INFO**.
- ❓ **Khi đệm đầy 100% với `neverBlock=true` thì sao?** ➔ 💡 **Drop log mới chứ KHÔNG BAO GIỜ làm nghẽn (block) REST API**.
- 🔑 **Keyword cốt lõi cần nhớ**: **Tomcat Thread (Put RAM Queue < 0.1ms) ➔ Async Daemon Thread (Batch Write Disk) ➔ Discarding Threshold (Drop Info when 80% Full)**.

**Answer outline:**
- **Phân tách Trách nhiệm Thread (Thread Separation)**:
  - Tomcat Worker Thread xử lý REST API chỉ nạp MDC context, quăng `LogEvent` vào Async Queue / RingBuffer RAM trong tiệm cận < 0.1ms.
  - Background `AsyncAppender Daemon Thread` lấy log ra khỏi Queue theo lô (Batching) để flush xuống OS Page Cache.
- **Cơ chế Bảo vệ Tự vệ Bộ nhớ (Memory Overflow Protection)**:
  - `discardingThreshold` (mặc định 20% bộ nhớ trống còn lại - tức đầy 80%): Tự động drop log `TRACE`, `DEBUG`, `INFO` để dành dung lượng cho `WARN`, `ERROR`.
  - `neverBlock=true`: Nếu RingBuffer đầy 100%, vứt bỏ log mới thay vì block Tomcat Worker Thread ➔ API nghiệp vụ luôn sẵn sàng 100%.<br>
**Required trade-offs:** Chấp nhận mất một số dòng log `INFO` khi hệ thống bị nghẽn đĩa nặng để bảo toàn tính sẵn sàng cho REST API.<br>
**Follow-up ladder:** Sự khác biệt giữa BlockingQueue truyền thống và LMAX Disruptor RingBuffer trong Async Logging là gì?<br>
**Red flags:** Cho rằng Tomcat Worker Thread vừa xử lý logic vừa tự thực thi I/O ghi file đĩa đồng bộ.
