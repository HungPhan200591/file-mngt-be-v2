# 🧵 Virtual Threads: Overview & Fundamentals

Tài liệu giải thích chuyên sâu về **Virtual Threads** (Project Loom) từ Java 21 / JDK 25: Khái niệm cốt lõi, so sánh mô hình Platform Threads (OS Threads) với Virtual Threads, cơ chế Mount/Unmount trên Carrier Threads, và kiến trúc luồng dữ liệu của JVM.

---

## 1. Ý tưởng Cốt lõi & Sự Tiến hóa của Threading trong Java

### 🧠 Đặt vấn đề: Mô hình 1:1 Platform Threads (OS Threads)
Từ các phiên bản Java đầu tiên cho đến Java 20, Java áp dụng mô hình **1:1 Threading**:
- **1 Java Thread = 1 OS Platform Thread** (Kernel Thread do Hệ điều hành quản lý).
- **Tốn bộ nhớ**: Mỗi OS Thread chiếm giữ khoảng **1MB RAM** bộ nhớ Stack (Native Stack Memory).
- **Tốn CPU**: Khi OS chuyển đổi giữa các thread (**Context Switching**), CPU phải lưu lại trạng thái registers, nhịp đồng hồ và nhường quyền cho thread khác. Chi phí này rất đắt đỏ.
- **Giới hạn số lượng**: Một máy chủ thông thường chỉ chịu đựng được khoảng 1,000 - 5,000 OS Threads trước khi cạn RAM hoặc sập hệ thống do CPU thrashing.

```mermaid
flowchart TB
    subgraph OLD["Mô hình Truyền thống (Platform Threads 1:1)"]
        J1["<font color='white'>Java Thread 1</font>"] <-->|"1:1 Mapping"| OS1["<font color='white'>OS Kernel Thread 1 (1MB RAM)</font>"]
        J2["<font color='white'>Java Thread 2</font>"] <-->|"1:1 Mapping"| OS2["<font color='white'>OS Kernel Thread 2 (1MB RAM)</font>"]
    end

    subgraph NEW["Mô hình Virtual Threads (M:N Loom)"]
        VT1["<font color='white'>Virtual Thread 1 (Few KB)</font>"] --> CT1
        VT2["<font color='white'>Virtual Thread 2 (Few KB)</font>"] --> CT1
        VT3["<font color='white'>Virtual Thread 3 (Few KB)</font>"] --> CT1["<font color='white'>Carrier OS Thread (ForkJoinPool)</font>"]
    end

    style J1 fill:#FF9800,stroke:#fff,stroke-width:2px
    style OS1 fill:#9C27B0,stroke:#fff,stroke-width:2px
    style J2 fill:#FF9800,stroke:#fff,stroke-width:2px
    style OS2 fill:#9C27B0,stroke:#fff,stroke-width:2px
    style VT1 fill:#4CAF50,stroke:#fff,stroke-width:2px
    style VT2 fill:#4CAF50,stroke:#fff,stroke-width:2px
    style VT3 fill:#4CAF50,stroke:#fff,stroke-width:2px
    style CT1 fill:#2196F3,stroke:#fff,stroke-width:2px
```

---

## 2. Cơ chế Mount và Unmount trên Carrier Threads

Mấu chốt giúp Virtual Threads đạt được số lượng hàng triệu concurrent threads nằm ở cơ chế **Continuation** và **Carrier Threads**:

### 🔄 Quy trình Xử lý I/O:
1. **Mount (Gắn)**: Khi một Virtual Thread bắt đầu chạy, JVM sẽ gắn (mount) nó vào một OS Thread thực sự gọi là **Carrier Thread** (thường dùng `ForkJoinPool`).
2. **Unmount (Gỡ khi I/O Wait)**: Khi Virtual Thread thực hiện một thao tác blocking I/O (như đọc đĩa, gọi Database, chờ HTTP response):
   - JVM lưu lại trạng thái Continuation (Call Stack) của Virtual Thread vào RAM Heap (chỉ ngốn vài KB).
   - JVM lập tức **gỡ (unmount)** Virtual Thread đó khỏi Carrier Thread.
   - Carrier Thread trống sẽ tiếp tục gánh cho Virtual Thread khác chạy.
3. **Remount (Gắn lại khi I/O xong)**: Khi thao tác I/O hoàn tất, Virtual Thread được đưa vào danh sách sẵn sàng và JVM sẽ gắn (remount) nó lại vào một Carrier Thread rảnh rỗi bất kỳ để chạy nấc logic tiếp theo.

```mermaid
flowchart TB
    VT["<font color='white'>Virtual Thread (Task Execution)</font>"] -->|"1 - Mount on Carrier"| CT["<font color='white'>Carrier OS Thread</font>"]
    VT -->|"2 - Blocking I/O Occurs"| UNMOUNT["<font color='white'>JVM Unmounts VT & Saves Stack to Heap</font>"]
    UNMOUNT -->|"3 - Carrier Thread Freed"| FREE["<font color='white'>Carrier Runs Other Virtual Threads</font>"]
    UNMOUNT -->|"4 - I/O Complete"| REMOUNT["<font color='white'>JVM Remounts VT to Carrier</font>"]

    style VT fill:#4CAF50,stroke:#fff,stroke-width:2px
    style CT fill:#2196F3,stroke:#fff,stroke-width:2px
    style UNMOUNT fill:#FF9800,stroke:#fff,stroke-width:2px
    style FREE fill:#009688,stroke:#fff,stroke-width:2px
    style REMOUNT fill:#9C27B0,stroke:#fff,stroke-width:2px
```

---

## 3. Bảng So sánh Chi tiết: Platform Threads vs Virtual Threads

| Tiêu chí | Platform Threads (OS Threads) | Virtual Threads (JVM-Managed) |
| :--- | :--- | :--- |
| **Quản lý bởi** | OS Kernel | JVM (Java Virtual Machine) |
| **Dung lượng Stack RAM** | ~1 MB / Thread | ~Kài KB / Thread (nhẹ hơn 1000x) |
| **Chi phí Tạo mới** | Cực kỳ đắt đỏ (Phải gọi OS System Call) | Cực kỳ rẻ (Như tạo một Java Object thông thường) |
| **Mô hình Pool** | Bắt buộc dùng Thread Pool (`ExecutorService`, `ThreadPoolExecutor`) để tái sử dụng. | **Không cần Thread Pool**. Tạo mới 1 Virtual Thread cho mỗi task. |
| **Số lượng Tối đa** | Vài nghìn (1,000 - 5,000 Threads) | Hàng triệu (1,000,000+ Threads) |
| **Phù hợp với** | CPU-Bound Tasks (Tính toán nặng, Mã hóa) | I/O-Bound Tasks (DB Query, Network HTTP, FileSystem) |

---

## 4. Thông số Mặc định, Giới hạn Tối đa & Con số Tối ưu

### 🔢 1. Mặc định là bao nhiêu Thread?
- **Virtual Threads**: **KHÔNG CÓ con số mặc định cố định** và **KHÔNG DÙNG THREAD POOL**. Khi có 1 task mới, JVM sinh ra 1 Virtual Thread mới trên RAM Heap và tự hủy sau khi task xong (`newVirtualThreadPerTaskExecutor()`).
- **Carrier OS Threads (OS Threads bên dưới)**: Mặc định bằng đúng **số lượng CPU Cores khả dụng** (`Runtime.getRuntime().availableProcessors()`), tối thiểu là `2`. *(Ví dụ: Máy chủ 8 Cores $\rightarrow$ 8 Carrier OS Threads)*.

### 🚀 2. Tối đa là bao nhiêu?
- **Virtual Threads Tối đa**: **Unbounded (Không có giới hạn cứng)** từ cấu hình JVM. Giới hạn duy nhất là bộ nhớ RAM Heap (`-Xmx`). Một máy chủ 16GB RAM có thể chạy dễ dàng **1,000,000 đến vài triệu Virtual Threads** cùng lúc.
- **Carrier OS Threads Tối đa**: Mặc định tối đa là **256 OS Threads** trong Carrier `ForkJoinPool`.

### 🎯 3. Con số Tối ưu là bao nhiêu?
- **Nguyên tắc cốt lõi**: **`1 Task = 1 Virtual Thread`**. Có 10,000 tasks $\rightarrow$ Tạo 10,000 Virtual Threads. Không cần tính toán Pool Size như trước.
- **Giới hạn Tải**: Việc khống chế số lượng không nằm ở Virtual Threads mà ở Downstream Resources (như Database Connection Pool). Sử dụng **`Semaphore`** (ví dụ `Semaphore(20)`) để giới hạn số Virtual Threads được phép truy cập Database đồng thời.

---

## 5. Bộ Cờ Cấu hình (JVM Flags & Spring Boot Configs)

### ⚙️ 1. Cấu hình Carrier OS Thread Pool (`ForkJoinPool`)
Chạy bằng các cờ JVM System Properties (`-D...`):

| JVM Property | Ý nghĩa | Giá trị Mặc định |
| :--- | :--- | :--- |
| **`jdk.virtualThreadScheduler.parallelism`** | Số lượng Carrier OS Threads thực sự chạy song song. | Số CPU Cores (`availableProcessors()`) |
| **`jdk.virtualThreadScheduler.maxPoolSize`** | Số lượng OS Threads tối đa mà Carrier Pool được phép mở rộng. | `256` |
| **`jdk.virtualThreadScheduler.minRunnable`** | Số lượng Carrier Threads tối thiểu luôn sẵn sàng chạy. | `1` |

*Ví dụ truyền cờ JVM:*
```powershell
java -Djdk.virtualThreadScheduler.parallelism=16 -Djdk.virtualThreadScheduler.maxPoolSize=512 -jar app.jar
```

### ⚙️ 2. Cấu hình Tracking & Debug Thread Pinning
Dùng để phát hiện khi Virtual Thread bị khóa cứng vào OS Thread do `synchronized`:

| JVM Property | Giá trị | Ý nghĩa |
| :--- | :--- | :--- |
| **`jdk.tracePinnedThreads`** | `short` | In ra stack trace ngắn gọn trên console khi có Virtual Thread bị Pinned. |
| **`jdk.tracePinnedThreads`** | `full` | In ra toàn bộ stack trace chi tiết khi bị Pinned. |

### ⚙️ 3. Cấu hình trong Spring Boot 3.4+ (`application.yml`)

```yaml
spring:
  threads:
    virtual:
      enabled: ${SCAN_VIRTUAL_THREADS_ENABLED:false}
```
- Khi `enabled: true`: Spring Boot chuyển Tomcat Web Server, `@Async` và `TaskExecutor` sang sử dụng Virtual Threads.

