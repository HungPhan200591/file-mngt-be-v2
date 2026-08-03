# 🎯 Ngân Hàng Câu Hỏi Phỏng Vấn: Virtual Threads & Concurrency (JDK 25 / Spring Boot 3.4+)

Tài liệu tổng hợp các câu hỏi phỏng vấn chuẩn **Senior / Architect** chuyên sâu về **Virtual Threads (Project Loom)**, so sánh các mô hình Concurrency, các kịch bản hỏi xoáy rẽ nhánh (Multi-Branch Follow-up Ladder), và chiến lược xử lý sự cố Production.

---

## 📊 Ma trận Phân bổ Câu hỏi (Coverage Matrix)

| ID | Chủ đề | Cấp độ | Tần suất xuất hiện |
| :--- | :--- | :--- | :--- |
| `VT-001` | Bản chất Virtual Threads vs Platform Threads & Cơ chế Mount/Unmount | `SENIOR` | 🔥 HIGH |
| `VT-002` | So sánh Virtual Threads với Goroutines, Coroutines & Node.js Event Loop | `SENIOR` | 🔥 HIGH |
| `VT-003` | Use Cases, Thread Pinning (`synchronized`), Connection Exhaustion & Best Practices | `ARCHITECT` | 🔥 HIGH |
| `VT-004` | Structured Concurrency & Scoped Values trong Java 21+ / JDK 25 | `ARCHITECT` | ⚡ MEDIUM |
| `VT-005` | Production Observability, JFR Events & Troubleshooting Virtual Threads | `ARCHITECT` | 🔥 HIGH |

---

## 📚 Chi Tiết Ngân Hàng Câu Hỏi Phỏng Vấn & Kịch Bản Rẽ Nhánh

### VT-001 — `[LEVEL: SENIOR]`
**Question:** Virtual Threads trong Java 21+ / JDK 25 là gì? Nó giải quyết bài toán gì và khác biệt cốt lõi như thế nào so với Platform Threads (OS Threads) truyền thống?<br>
**Target depth:** `D3` · **Interview likelihood:** `HIGH` · **Question type:** `COMMON_CORE`<br>
**Interviewer evaluates:** Đánh giá sự hiểu biết của ứng viên về kiến trúc Threading của JVM, bộ nhớ Stack, và cơ chế chuyển đổi ngữ cảnh (Context Switching).<br>

⚡ **Trả lời siêu ngắn (Elevator Pitch)**:
> *"Virtual Threads là các thread siêu nhẹ do JVM tự quản lý (chỉ tốn vài KB RAM Heap), thay vì bị ánh xạ 1:1 đắt đỏ với OS Platform Threads (~1MB RAM Stack). Nó cho phép JVM xử lý hàng triệu concurrent requests bất đồng bộ nhưng vẫn giữ phong cách code synchronous đơn giản."*

🧠 **Chuỗi Hỏi - Đáp Keyword (Memory Flashcard Chain)**:
- ❓ **Khác biệt bộ nhớ giữa Platform Thread và Virtual Thread?** ➔ 💡 **Platform Thread ngốn ~1MB OS Stack RAM**, còn **Virtual Thread chỉ ngốn vài KB RAM Heap**.
- ❓ **Chuyện gì xảy ra khi Virtual Thread gặp blocking I/O?** ➔ 💡 **JVM tự động unmount Virtual Thread khỏi Carrier Thread** và nhường OS Thread cho task khác.
- ❓ **Carrier Thread trong Virtual Threads là gì?** ➔ 💡 **Là các OS Platform Threads chạy bên dưới** (thường từ `ForkJoinPool`), làm 'xe chở' các Virtual Threads.
- ❓ **Continuation object trong JVM giữ vai trò gì?** ➔ 💡 **Lưu trữ Call Stack và trạng thái thực thi** của Virtual Thread trên RAM Heap khi bị unmount.
- ❓ **Virtual Thread có giúp 1 request đơn lẻ chạy nhanh hơn không?** ➔ 💡 **KHÔNG**. Nó chỉ làm tăng **Throughput** (lưu lượng xử lý tổng), không làm giảm **Latency** của 1 request đơn.
- ❓ **Thread Pool cho Virtual Threads có cần thiết không?** ➔ 💡 **KHÔNG BAO GIỜ pool Virtual Threads**. Hãy dùng `newVirtualThreadPerTaskExecutor()` tạo mới cho từng task.
- 🔑 **Keyword cốt lõi cần nhớ**: **1:1 vs M:N — Continuation Heap Stack — Unmount/Remount — Carrier Thread — No-Pooling Rule**.

**Answer outline:**
- **Bản chất Platform Threads (1:1)**: Mỗi Java Thread map 1:1 với OS Kernel Thread. Giới hạn 1,000 - 5,000 threads do cạn RAM Stack và đắt đỏ khi Context Switching ở Kernel.
- **Bản chất Virtual Threads (M:N - Loom)**: JVM quản lý hàng triệu Virtual Threads đè lên một số lượng rất ít Carrier OS Threads.
- **Quy trình Mount/Unmount**: Khi gặp I/O Wait (DB, Disk, HTTP), JVM lưu Call Stack (Continuation) vào Heap và unmount khỏi Carrier Thread. Khi I/O hoàn tất, JVM remount Virtual Thread vào Carrier Thread rảnh để chạy tiếp.
- **Ứng dụng trong Backend V2**: Tích hợp trong `scan-service` để quét I/O bất đồng bộ via cờ `spring.threads.virtual.enabled`.

**Required trade-offs:** Virtual Threads tăng Throughput tổng của hệ thống chứ không làm 1 request đơn lẻ chạy nhanh hơn.<br>

🪜 **Cây Kịch Bản Hỏi Xoáy (Multi-Branch Follow-up Ladder)**:
- 🌿 **Kịch bản 1: JVM Internals & Memory Structure**
  - ❓ *Hỏi xoáy 1.1*: "Nếu có 1,000,000 Virtual Threads cùng bị unmount, bộ nhớ Heap có rủi ro OOM không?"
  - 💡 *Đáp 1.1*: Có, nếu mỗi Virtual Thread mang một Call Stack quá sâu hoặc lưu các Object lớn trong Local Variables. JVM lưu Continuation Stack trên Heap nên OOM Heap vẫn xảy ra nếu không kiểm soát bộ nhớ.
  - ❓ *Hỏi xoáy 1.2*: "Kích thước mặc định của `ForkJoinPool` làm Carrier Threads là bao nhiêu?"
  - 💡 *Đáp 1.2*: Mặc định bằng **số lượng CPU Cores khả dụng** (`Runtime.getRuntime().availableProcessors()`), tối thiểu là 2.
- 🌿 **Kịch bản 2: Scheduling & Thread State**
  - ❓ *Hỏi xoáy 2.1*: "Thread State của Virtual Thread hiển thị gì khi nó đang trong trạng thái Unmounted I/O Wait?"
  - 💡 *Đáp 2.1*: Trạng thái hiển thị là `WAITING` hoặc `TIMED_WAITING`, nhưng nó KHÔNG chiếm giữ bất kỳ OS Thread nào bên dưới.

**Red flags:** Trả lời nhầm lẫn rằng Virtual Threads chạy không cần OS Threads, hoặc tạo Thread Pool để reuse Virtual Threads (`newFixedThreadPool` cho Virtual Threads).

---

### VT-002 — `[LEVEL: SENIOR]`
**Question:** So sánh Virtual Threads của Java với Go Goroutines, Kotlin Coroutines và Node.js Event Loop? Tại sao Java không chọn cú pháp `async/await` như C# hay Node.js?<br>
**Target depth:** `D3` · **Interview likelihood:** `HIGH` · **Question type:** `COMMON_SCENARIO`<br>
**Interviewer evaluates:** Đánh giá tầm nhìn so sánh đa ngôn ngữ (Polyglot concurrency), khả năng phân tích bài toán "Function Coloring".<br>

⚡ **Trả lời siêu ngắn (Elevator Pitch)**:
> *"Java Virtual Threads và Go Goroutines đi theo triết lý 'Transparent Blocking' — dev viết code đồng bộ tự nhiên mà không bị 'Function Coloring' (không cần `async/await` hay `suspend`). Trong khi Kotlin Coroutines và Node.js bắt buộc dev phải khai báo rõ ràng ở cấp độ biên dịch."*

🧠 **Chuỗi Hỏi - Đáp Keyword (Memory Flashcard Chain)**:
- ❓ **'Function Coloring Problem' là gì?** ➔ 💡 **Lỗi thiết kế khiến hàm `async`/`suspend` lây lan khắp codebase**, làm đổi cách gọi của mọi caller.
- ❓ **Java Virtual Threads khác gì Kotlin Coroutines?** ➔ 💡 **Java xử lý ở cấp JVM Runtime** (không đổi code), còn **Kotlin xử lý ở cấp Compiler** (phải viết `suspend`).
- ❓ **Java Virtual Threads khác gì Node.js Event Loop?** ➔ 💡 **Node.js chỉ có 1 Single Thread**, còn **Java tận dụng tối đa Multi-core CPU** qua nhiều Carrier Threads.
- ❓ **Điểm khác biệt chính giữa Go Goroutines và Java Virtual Threads?** ➔ 💡 **Go có Preemptive Scheduler** (ngắt tính toán CPU), còn **Java Virtual Threads dựa trên Cooperative I/O Unmount**.
- ❓ **Tại sao Java từ chối cú pháp `async/await`?** ➔ 💡 Để **tương thích ngược 100%** với hàng tỷ dòng code Java 20 năm qua (JDBC, Spring MVC) mà không ép rewrite.
- ❓ **Tại sao không rewrite mà code cũ vẫn chạy được Virtual Threads?** ➔ 💡 Vì **JDK đã "thay ruột" ngầm các API Blocking I/O** (như `Socket.read()`) sang `VirtualThread.park()`, và `VirtualThread extends Thread`.
- 🔑 **Keyword cốt lõi cần nhớ**: **Transparent vs Explicit — Function Coloring — JVM I/O Hooking — VirtualThread extends Thread**.

**Answer outline:**
- **Bảng so sánh 4 mô hình**: Runtime-managed (Java, Go) vs Compiler/Library-managed (Kotlin, Node.js).
- **Bản chất KHÔNG CẦN Rewrite của Java**:
  1. **JVM Internal I/O Hooking**: JDK 21+ sửa ruột toàn bộ các hàm Blocking I/O gốc (`Socket.read()`, `InputStream.read()`, `LockSupport.park()`). Khi phát hiện chạy trên Virtual Thread, JDK tự động gọi `VirtualThread.park()` unmount khỏi Carrier Thread thay vì gửi OS Blocking Call.
  2. **Tương thích `java.lang.Thread`**: `VirtualThread` kế thừa trực tiếp `java.lang.Thread`, giúp tất cả frameworks/libraries cổ điển (Tomcat, Spring, Hibernate, JDBC) nhận diện là 1 Thread chuẩn mà không đổi kiểu trả về (`String` vẫn là `String`, không thành `Promise`/`Future`).
- **Khác biệt Go vs Java**: Go có Preemptive Scheduler (Go 1.14+), còn Java Virtual Threads hiện chủ yếu dựa vào I/O Blocking / Park points và bị dính cạm bẫy Thread Pinning nếu dùng `synchronized`.

**Required trade-offs:** Chấp nhận độ phức tạp cao ở mức JVM internals để đổi lại trải nghiệm viết code cực kỳ sạch cho Developer.<br>

🪜 **Cây Kịch Bản Hỏi Xoáy (Multi-Branch Follow-up Ladder)**:
- 🌿 **Kịch bản 1: Migration Strategy & Legacy Interop**
  - ❓ *Hỏi xoáy 1.1*: "Nếu dự án Kotlin đang dùng `kotlinx.coroutines`, có nên đập đi chuyển sang Java Virtual Threads không?"
  - 💡 *Đáp 1.1*: Không cần thiết. Kotlin Coroutines cung cấp thêm các khái niệm Structured Concurrency cao cấp (Channels, Flow, Cancellation Propagation). Ta có thể kết hợp chạy Kotlin Coroutines trên `Dispatchers.LOOM` (Virtual Threads dispatcher).
- 🌿 **Kịch bản 2: CPU-Bound Edge Cases**
  - ❓ *Hỏi xoáy 2.1*: "Nếu một service Node.js và một service Java Virtual Threads cùng chạy một hàm hash MD5 file 1GB, bên nào thắng?"
  - 💡 *Đáp 2.1*: Java sẽ thắng nếu tận dụng Multi-core Carrier Threads. Node.js bị nghẽn trên 1 Main Thread ngoại trừ khi chủ động dùng `worker_threads`. Cả hai đều không tối ưu cho CPU-bound, nhưng Java xử lý đa lõi tốt hơn.

**Red flags:** Ngộ nhận Node.js chạy đa luồng cho code Javascript, hoặc không giải thích được bài toán "Function Coloring".

---

### VT-003 — `[LEVEL: ARCHITECT]`
**Question:** Những cạm bẫy (Pitfalls) và Đánh đổi (Trade-offs) lớn nhất khi đưa Virtual Threads vào hệ thống Enterprise Production là gì? Làm thế nào để xử lý sự cố Thread Pinning và Connection Pool Exhaustion?<br>
**Target depth:** `D4` · **Interview likelihood:** `HIGH` · **Question type:** `ARCHITECTURE_EVOLUTION`<br>
**Interviewer evaluates:** Đánh giá kinh nghiệm thực chiến Production, tư duy phòng ngừa rủi ro hệ thống (Fail-safe Architecture).<br>

⚡ **Trả lời siêu ngắn (Elevator Pitch)**:
> *"Cạm bẫy lớn nhất là Thread Pinning khi dùng từ khóa `synchronized` làm khóa OS Thread, ngốn RAM do lưu Object lớn vào `ThreadLocal`, và nguy cơ làm sập Downstream Database do xả hàng nghìn concurrent queries vượt quá HikariCP Connection Pool limit."*

🧠 **Chuỗi Hỏi - Đáp Keyword (Memory Flashcard Chain)**:
- ❓ **'Thread Pinning' xảy ra khi nào và cách khắc phục?** ➔ 💡 Xảy ra trong khối **`synchronized`**, khắc phục bằng cách chuyển sang **`ReentrantLock`**.
- ❓ **Tại sao Virtual Threads lại có thể đánh sập Database?** ➔ 💡 Vì **Virtual Threads xả 10,000 queries đồng thời**, vượt xa giới hạn **HikariCP Connection Pool**.
- ❓ **Thay thế `ThreadLocal` bằng gì trong Java 21+?** ➔ 💡 Sử dụng **`ScopedValue`** (`java.lang.ScopedValue`).
- ❓ **Công cụ nào phát hiện Thread Pinning trên Production?** ➔ 💡 **Java Flight Recorder (JFR)** với event `jdk.VirtualThreadPinned`.
- ❓ **Giải pháp chống cạn kiệt DB Connection Pool khi dùng Virtual Threads?** ➔ 💡 Dùng **`Semaphore`** để giới hạn số Virtual Threads được phép đụng vào DB.
- 🔑 **Keyword cốt lõi cần nhớ**: **Thread Pinning (`ReentrantLock`) — Database Connection Exhaustion (`Semaphore`) — `ThreadLocal` Leak (`ScopedValue`) — JFR Tracking**.

**Answer outline:**
- **Bài toán Thread Pinning**: Giải thích cơ chế block Carrier Thread khi gặp `synchronized` hoặc Native C calls. Hướng dẫn refactor code.
- **Bài toán Database Pool Exhaustion**: Phân tích việc Tomcat nhận được 10k requests nhưng HikariCP chỉ có 10 connections. Hướng dẫn dùng `Semaphore` hoặc Rate Limiter để bảo vệ DB.
- **Bài toán CPU-Bound Task**: Giải thích lý do không dùng Virtual Threads cho Hashing/Video Transcoding.
- **Checklist Best Practices**: Không dùng Thread Pool cho Virtual Threads (`Executors.newVirtualThreadPerTaskExecutor()`), rà soát `ThreadLocal` và benchmark kỹ trước khi bật cờ `spring.threads.virtual.enabled=true`.

**Required trade-offs:** Phải kiểm soát lưu lượng truy cập xuống Database bằng Semaphore để tránh hiện tượng Connection Starvation.<br>

🪜 **Cây Kịch Bản Hỏi Xoáy (Multi-Branch Follow-up Ladder)**:
- 🌿 **Kịch bản 1: Production Troubleshooting & JFR Tuning**
  - ❓ *Hỏi xoáy 1.1*: "Làm sao để cấu hình JVM tự động in warning khi một Virtual Thread bị Pinned quá 20ms?"
  - 💡 *Đáp 1.1*: Thêm VM Option: `-Djdk.tracePinnedThreads=short` (in stack trace ngắn) hoặc `-Djdk.tracePinnedThreads=full` (in đầy đủ stack trace).
- 🌿 **Kịch bản 2: Database Connection Strategy**
  - ❓ *Hỏi xoáy 2.1*: "Nếu không muốn dùng `Semaphore` trong application code, có cách nào bảo vệ PostgreSQL ở hạ tầng không?"
  - 💡 *Đáp 2.1*: Sử dụng **PgBouncer** làm Database Proxy phía trước PostgreSQL để xử lý Connection Pooling ở hạ tầng, hoặc chuyển sang dùng Reactive Database Drivers (R2DBC) nếu phù hợp.

**Red flags:** Khẳng định bật Virtual Threads sẽ luôn làm hệ thống chạy nhanh hơn trong mọi trường hợp, hoặc không hề biết đến khái niệm Thread Pinning.

---

### VT-004 — `[LEVEL: ARCHITECT]`
**Question:** Structured Concurrency và Scoped Values trong Java 21+ / JDK 25 là gì? Chúng giải quyết những hạn chế nào của `ExecutorService` và `ThreadLocal` khi làm việc với Virtual Threads?<br>
**Target depth:** `D4` · **Interview likelihood:** `MEDIUM` · **Question type:** `ARCHITECTURE_EVOLUTION`<br>
**Interviewer evaluates:** Đánh giá mức độ cập nhật các tính năng chuẩn hóa mới của Java 21+ / JDK 25 (Project Loom Phase 2).<br>

⚡ **Trả lời siêu ngắn (Elevator Pitch)**:
> *"Structured Concurrency coi nhóm các sub-task bất đồng bộ như một khối lệnh có phạm vi (scoped block) duy nhất, đảm bảo nếu 1 task lỗi thì toàn bộ sub-tasks bị cancel lập tức. Scoped Values thay thế `ThreadLocal` để truyền dữ liệu bất biến (Immutable Data) an toàn và cực kỳ nhẹ bộ nhớ giữa các Virtual Threads."*

🧠 **Chuỗi Hỏi - Đáp Keyword (Memory Flashcard Chain)**:
- ❓ **Unstructured Concurrency trong `ExecutorService` bị lỗi gì?** ➔ 💡 **Orphaned Tasks (Task mồ côi)** vẫn tiếp tục chạy ngốn tài nguyên dù main task đã bị timeout/hủy.
- ❓ **Structured Concurrency quản lý vòng đời sub-tasks như thế nào?** ➔ 💡 **Ràng buộc vòng đời của sub-tasks vào phạm vi của parent scope** via `StructuredTaskScope`.
- ❓ **Chiến lược `ShutdownOnFailure` làm gì?** ➔ 💡 Hủy toàn bộ các sub-tasks khác ngay khi **CÓ 1 sub-task bị quăng Exception**.
- ❓ **Chiến lược `ShutdownOnSuccess` làm gì?** ➔ 💡 Trả về kết quả ngay khi **CÓ 1 sub-task chạy thành công đầu tiên** và hủy các tasks còn lại.
- ❓ **Tại sao `ScopedValue` tốt hơn `ThreadLocal` khi dùng Virtual Threads?** ➔ 💡 **Dữ liệu là Immutable (Bất biến)**, tự hủy khi thoát scope, không bị rò rỉ RAM Heap.
- 🔑 **Keyword cốt lõi cần nhớ**: **StructuredTaskScope — ShutdownOnFailure — ShutdownOnSuccess — ScopedValue Immutable — Orphan Task Elimination**.

**Answer outline:**
- **Vấn đề Unstructured Concurrency (`Future.get()`)**: Khi gọi 3 REST APIs song song bằng `ExecutorService`, nếu API 1 lỗi, API 2 & 3 vẫn vô tư chạy tiếp $\rightarrow$ Lãng phí I/O, RAM, Network và tạo ra Orphan Tasks.
- **Giải pháp `StructuredTaskScope`**: Bọc toàn bộ các sub-tasks vào khối `try-with-resources`. Vòng đời của child threads không bao giờ vượt quá parent scope.
- **Ứng dụng `ScopedValue`**: Thay thế `ThreadLocal` truyền `SecurityContext` hoặc `CorrelationId` xuyên suốt cây Virtual Threads một cách cực kỳ nhẹ nhàng.

**Required trade-offs:** Cần tư duy lập trình theo Scope thay vì văng các `Future` tự do khắp ứng dụng.<br>

🪜 **Cây Kịch Bản Hỏi Xoáy (Multi-Branch Follow-up Ladder)**:
- 🌿 **Kịch bản 1: Error Handling & Short-Circuiting**
  - ❓ *Hỏi xoáy 1.1*: "Trong mô hình microservices, làm sao dùng Structured Concurrency để triển khai kịch bản Hedged Requests (gửi song song 2 request tới 2 instances, lấy kết quả nhanh nhất)?"
  - 💡 *Đáp 1.1*: Sử dụng `StructuredTaskScope.ShutdownOnSuccess<String>()`. Khi Instance A trả về kết quả thành công trước, scope lập tự động phát lệnh `cancel()` tới Instance B và trả về kết quả ngay.
- 🌿 **Kịch bản 2: Context Propagation trong Observability**
  - ❓ *Hỏi xoáy 2.1*: "Làm sao để truyền `X-Correlation-Id` từ Parent Virtual Thread sang Child Virtual Threads trong Structured Concurrency?"
  - 💡 *Đáp 2.1*: Dùng `ScopedValue.where(CORRELATION_ID, "corr_123").run(() -> { ... })`. Tất cả child Virtual Threads sinh ra bên trong scope này đều đọc được `CORRELATION_ID.get()` mà không tốn bộ nhớ sao chép.

**Red flags:** Nhầm lẫn Structured Concurrency với Reactive Programming (RxJava/Project Reactor), hoặc cho rằng `ScopedValue` có thể thay đổi dữ liệu (Mutable).

---

### VT-005 — `[LEVEL: ARCHITECT]`
**Question:** Khi ứng dụng Production chạy trên Virtual Threads gặp sự cố bị nghẽn (Stuck/Hang) hoặc tiêu tốn RAM bất thường, quy trình Troubleshooting, đọc JFR (Java Flight Recorder) và Monitoring của bạn diễn ra như thế nào?<br>
**Target depth:** `D4` · **Interview likelihood:** `HIGH` · **Question type:** `PROJECT_APPLICATION`<br>
**Interviewer evaluates:** Đánh giá năng lực vận hành Production (SRE / DevOps / Senior Backend Engineer), khả năng dùng tool định vị Root Cause.<br>

⚡ **Trả lời siêu ngắn (Elevator Pitch)**:
> *"Troubleshooting Virtual Threads dựa vào Java Flight Recorder (JFR) để soi các event `jdk.VirtualThreadPinned` và `jdk.VirtualThreadSubmitFailed`, dùng `jcmd` dump danh sách Virtual Threads thay vì Thread Dump truyền thống (`jstack`), và theo dõi Prometheus metric `jvm.threads.virtual`."*

🧠 **Chuỗi Hỏi - Đáp Keyword (Memory Flashcard Chain)**:
- ❓ **Lý do lệnh `jstack` truyền thống không hiệu quả với Virtual Threads?** ➔ 💡 `jstack` **chỉ dump Platform Threads**, không liệt kê hàng triệu Virtual Threads trên Heap.
- ❓ **Lệnh CLI nào dùng để dump toàn bộ Virtual Threads trong JDK 21+?** ➔ 💡 **`jcmd <pid> Thread.dump_to_file -format=json <file>`**.
- ❓ **Event JFR quan trọng nhất cần monitor khi dùng Virtual Threads là gì?** ➔ 💡 **`jdk.VirtualThreadPinned`** (theo dõi thread bị khóa chặt vào OS thread).
- ❓ **Metric Micrometer / Prometheus nào theo dõi Virtual Threads trong Spring Boot 3.4+?** ➔ 💡 **`jvm.threads.virtual.mounted`** và **`jvm.threads.virtual.queued`**.
- 🔑 **Keyword cốt lõi cần nhớ**: **JFR jdk.VirtualThreadPinned — jcmd Thread.dump_to_file — Json Thread Dump — jvm.threads.virtual Metrics**.

**Answer outline:**
- **Quy trình 4 Bước Troubleshooting Production**:
  1. **Bước 1 (Alerting)**: Phát hiện Latency tăng đột biến trên Grafana Dashboard (`http_server_requests_seconds`).
  2. **Bước 2 (Metric Inspection)**: Kiểm tra metric `jvm.threads.virtual.mounted` vs `jvm.threads.virtual.queued`. Nếu `queued` tăng vọt $\rightarrow$ Cạn kiệt Carrier Threads hoặc bị Pinning.
  3. **Bước 3 (JFR Recording)**: Bật JFR ghi âm 60 giây: `jcmd <pid> JFR.start name=vt_debug settings=profile`. Mở bằng JDK Mission Control (JMC) tìm event `jdk.VirtualThreadPinned`.
  4. **Bước 4 (Thread Dump Analysis)**: Chạy `jcmd <pid> Thread.dump_to_file -format=json /tmp/vt_dump.json` để phân tích Call Stack của các Virtual Threads đang bị `WAITING`.

**Required trade-offs:** Phải bật sẵn JFR Event Tracking trên Production với overhead cực thấp (<1% CPU).<br>

🪜 **Cây Kịch Bản Hỏi Xoáy (Multi-Branch Follow-up Ladder)**:
- 🌿 **Kịch bản 1: Root Cause Analysis trong Thực tế**
  - ❓ *Hỏi xoáy 1.1*: "Giả sử JFR báo event `VirtualThreadPinned` nằm ở một thư viện 3rd party (ví dụ: JDBC Driver cũ), bạn không thể sửa source code của thư viện đó thì xử lý thế nào?"
  - 💡 *Đáp 1.1*: Bọc lời gọi phương thức của thư viện 3rd party đó vào một `CompletableFuture` hoặc `TaskExecutor` chạy riêng trên **Platform Thread Pool chuyên dụng**, cách ly không cho nó chạy trên Virtual Thread chính của HTTP Server.
- 🌿 **Kịch bản 2: Metric Alarm Thresholds**
  - ❓ *Hỏi xoáy 2.1*: "Thiết lập cảnh báo (Alert Rule) Prometheus như thế nào để phát hiện ứng dụng bị Thread Pinning nghiêm trọng?"
  - 💡 *Đáp 2.1*: Cảnh báo khi tỷ lệ `rate(jvm_threads_virtual_pinned_total[5m]) > 10` kéo dài trong 3 phút, hoặc khi `jvm_threads_virtual_queued` duy trì ngưỡng cao liên tục.

**Red flags:** Dùng `jstack` để cố gắng debug hàng triệu Virtual Threads trên Production, hoặc không biết cách trích xuất file JFR để đọc.
