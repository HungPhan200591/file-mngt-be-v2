# 🎯 Ngân Hàng Câu Hỏi Phỏng Vấn: Virtual Threads & Concurrency (JDK 25 / Spring Boot 3.4+)

Tài liệu tổng hợp các câu hỏi phỏng vấn chuẩn **Senior / Architect** chuyên sâu về **Virtual Threads (Project Loom)**, so sánh các mô hình Concurrency và bài toán thiết kế hệ thống thực tế.

---

## 📊 Ma trận Phân bổ Câu hỏi (Coverage Matrix)

| ID | Chủ đề | Cấp độ | Tần suất xuất hiện |
| :--- | :--- | :--- | :--- |
| `VT-001` | Bản chất Virtual Threads vs Platform Threads & Cơ chế Mount/Unmount | `SENIOR` | 🔥 HIGH |
| `VT-002` | So sánh Virtual Threads với Goroutines, Coroutines & Node.js Event Loop | `SENIOR` | 🔥 HIGH |
| `VT-003` | Use Cases, Thread Pinning (`synchronized`), Connection Exhaustion & Best Practices | `ARCHITECT` | 🔥 HIGH |

---

## 📚 Chi Tiết Ngân Hàng Câu Hỏi Phỏng Vấn

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
- 🔑 **Keyword cốt lõi cần nhớ**: **1:1 vs M:N — Continuation Heap Stack — Unmount/Remount — Carrier Thread**.

**Answer outline:**
- **Bản chất Platform Threads (1:1)**: Mỗi Java Thread map 1:1 với OS Kernel Thread. Giới hạn 1,000 - 5,000 threads do cạn RAM Stack và đắt đỏ khi Context Switching ở Kernel.
- **Bản chất Virtual Threads (M:N - Loom)**: JVM quản lý hàng triệu Virtual Threads đè lên một số lượng rất ít Carrier OS Threads.
- **Quy trình Mount/Unmount**: Khi gặp I/O Wait (DB, Disk, HTTP), JVM lưu Call Stack (Continuation) vào Heap và unmount khỏi Carrier Thread. Khi I/O hoàn tất, JVM remount Virtual Thread vào Carrier Thread rảnh để chạy tiếp.
- **Ứng dụng trong Backend V2**: Tích hợp trong `scan-service` để quét I/O bất đồng bộ via cờ `spring.threads.virtual.enabled`.

**Required trade-offs:** Virtual Threads tăng Throughput tổng của hệ thống chứ không làm 1 request đơn lẻ chạy nhanh hơn.<br>
**Follow-up ladder:** Nhà tuyển dụng có thể hỏi xoáy: *"Cơ chế nào của JVM giúp lưu giữ stack của Virtual Thread khi unmount?"* (Trả lời: Continuation / Heap allocation).<br>
**Red flags:** Trả lời nhầm lẫn rằng Virtual Threads chạy không cần OS Threads, hoặc ngộ nhận Virtual Threads làm CPU tính toán nhanh hơn.

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
- 🔑 **Keyword cốt lõi cần nhớ**: **Transparent vs Explicit — Function Coloring — Runtime-driven vs Compiler-driven — Multi-core Parallelism**.

**Answer outline:**
- **Bảng so sánh 4 mô hình**: Runtime-managed (Java, Go) vs Compiler/Library-managed (Kotlin, Node.js).
- **Lý do Java từ chối `async/await`**: Để giữ tương thích ngược 100% với 20 năm codebase Java cổ điển (JDBC, Spring MVC, Servlet API) mà không bắt cộng đồng rewrite lại hàng tỷ dòng code.
- **Khác biệt Go vs Java**: Go có Preemptive Scheduler (Go 1.14+), còn Java Virtual Threads hiện chủ yếu dựa vào I/O Blocking / Park points và bị dính cạm bẫy Thread Pinning nếu dùng `synchronized`.

**Required trade-offs:** Chấp nhận độ phức tạp cao ở mức JVM internals để đổi lại trải nghiệm viết code cực kỳ sạch cho Developer.<br>
**Follow-up ladder:** *"Nếu Kotlin Coroutines đã có trên JVM, tại sao Oracle vẫn đầu tư Project Loom?"* (Trả lời: Tương thích toàn bộ hệ sinh thái thư viện C/Java cũ mà không bị lây nhiễm `suspend`).<br>
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
- 🔑 **Keyword cốt lõi cần nhớ**: **Thread Pinning (`ReentrantLock`) — Database Connection Exhaustion (`Semaphore`) — `ThreadLocal` Leak (`ScopedValue`) — CPU-Bound vs I/O-Bound**.

**Answer outline:**
- **Bài toán Thread Pinning**: Giải thích cơ chế block Carrier Thread khi gặp `synchronized` hoặc Native C calls. Hướng dẫn refactor code.
- **Bài toán Database Pool Exhaustion**: Phân tích việc Tomcat nhận được 10k requests nhưng HikariCP chỉ có 10 connections. Hướng dẫn dùng `Semaphore` hoặc Rate Limiter để bảo vệ DB.
- **Bài toán CPU-Bound Task**: Giải thích lý do không dùng Virtual Threads cho Hashing/Video Transcoding.
- **Checklist Best Practices**: Không dùng Thread Pool cho Virtual Threads (`Executors.newVirtualThreadPerTaskExecutor()`), rà soát `ThreadLocal` và benchmark kỹ trước khi bật cờ `spring.threads.virtual.enabled=true`.

**Required trade-offs:** Phải kiểm soát lưu lượng truy cập xuống Database bằng Semaphore để tránh hiện tượng Connection Starvation.<br>
**Follow-up ladder:** *"Làm sao để phát hiện Virtual Thread có đang bị Pinning trên Production?"* (Trả lời: Dùng Java Flight Recorder - JFR event `jdk.VirtualThreadPinned`).<br>
**Red flags:** Khẳng định bật Virtual Threads sẽ luôn làm hệ thống chạy nhanh hơn trong mọi trường hợp, hoặc không hề biết đến khái niệm Thread Pinning.
