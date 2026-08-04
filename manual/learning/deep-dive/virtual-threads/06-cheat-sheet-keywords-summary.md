# ⚡ Virtual Threads & Concurrency: Cheat-Sheet & Core Keywords

Tài liệu tổng hợp **Cheat-Sheet từ khóa cốt lõi** và **định nghĩa siêu ngắn gọn (1-Line Definitions)** cho toàn bộ chủ đề **Virtual Threads (Project Loom)**, sẵn sàng cho việc tra cứu nhanh và ôn tập phỏng vấn.

---

## 🚀 1. Virtual Threads (Project Loom)
- **Định nghĩa 1 dòng**: Thread siêu nhẹ do JVM quản lý hoàn toàn trên RAM Heap (~vài KB), chạy đè lên một số ít OS Threads bên dưới để tăng tối đa tổng lưu lượng xử lý (**Throughput**).
- 🔑 **Keywords**: `M:N Mapping` — `JVM-Managed` — `Heap Memory (~few KB)` — `High Throughput`.

---

## 🔄 2. Cơ chế Mount / Unmount & Continuation
- **Định nghĩa 1 dòng**: Khi đụng Blocking I/O, JVM tự tháo (unmount) Virtual Thread ra khỏi OS Thread, cất Call Stack (`Continuation`) lên Heap; khi I/O xong thì gắn (remount) lại OS Thread rảnh để chạy tiếp.
- 🔑 **Keywords**: `Continuation (Call Stack)` — `Carrier Thread (ForkJoinPool)` — `Mount / Unmount` — `Non-blocking OS Thread`.

---

## 📌 3. Thread Pinning (`synchronized` vs `ReentrantLock`)
- **Định nghĩa 1 dòng**: Virtual Thread bị dính chặt vào OS Thread khi nằm trong khối `synchronized` (do Native C++ Lock Record trên OS Stack), làm sập hệ thống do cạn Carrier Threads; khắc phục bằng `ReentrantLock` (Pure Java AQS trên Heap).
- 🔑 **Keywords**: `Native C++ Monitor Frame` — `Carrier Starvation` — `ReentrantLock (AQS Heap)` — `LockSupport.park()`.

---

## 🎟️ 4. No-Pooling Rule & Semaphore Throttling
- **Định nghĩa 1 dòng**: Tuân thủ triết lý `1 Task = 1 Virtual Thread` (tuyệt đối **không pool** Virtual Threads); dùng `Semaphore(N)` bọc khối code DB/External API để chống xả tải làm sập Database.
- 🔑 **Keywords**: `No-Pooling Rule` — `Semaphore(N)` — `Resource Throttling` — `Permits Counter` — `finally { release(); }`.

---

## 🎨 5. Function Coloring & So sánh Polyglot
- **Định nghĩa 1 dòng**: Java dùng triết lý `Transparent Blocking` (JVM tự thay ruột API I/O ngầm), **không bị lỗi lây nhiễm hàm (Function Coloring)** bởi `async/await` hay `suspend` như Node.js hay Kotlin.
- 🔑 **Keywords**: `Transparent Blocking` — `No Function Coloring` — `JVM I/O Hooking` — `java.lang.Thread Inheritance`.

---

## ⚙️ 6. Cấu hình & Troubleshooting Production
- **Định nghĩa 1 dòng**: Bật trong Spring Boot bằng `spring.threads.virtual.enabled: true`; debug Pinning bằng `-Djdk.tracePinnedThreads=short` / event JFR `jdk.VirtualThreadPinned`; dump thread bằng `jcmd Thread.dump_to_file`.
- 🔑 **Keywords**: `spring.threads.virtual.enabled` — `-Djdk.tracePinnedThreads` — `JFR jdk.VirtualThreadPinned` — `jcmd Thread.dump_to_file`.
