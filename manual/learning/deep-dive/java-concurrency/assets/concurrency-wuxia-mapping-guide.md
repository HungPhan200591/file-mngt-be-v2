# 📜 BÍ KÍP VÕ HỌC: QUY ĐỔI JAVA CONCURRENCY SANG KIẾM HIỆP

> **Tài liệu hướng dẫn chuyển thể (Mapping Guide)**: Ánh xạ toàn bộ kiến trúc Đa luồng Java Concurrency (Java 1.0 đến Java 21 / JDK 25) sang thế giới Võ hiệp / Tiên hiệp Kim Dung. Sử dụng làm tài liệu nguồn cho NotebookLM Audio Overview và Video Script.

---

## 🥋 BẢNG QUY ĐỔI KHÁI NIỆM (METAPHOR DICTIONARY)

### 1. Cõi Thực Thi (Execution Realm)
* **CPU Cores (8 Cores)**: *Bát Đại Cao Thủ / Hoàng Đế Tứ Đại* — Ngự trên điện cao, mỗi giây xuất 3 tỷ chiêu nhưng chỉ tiếp được số lượng đệ tử có hạn.
* **Platform Thread (Java 1.0)**: *Cự Thể Thiết Hán (Đệ tử Ngoại Môn)* — Cơ bắp cuồn cuộn, ăn khỏe (ngốn 1MB đan dược bộ nhớ Stack), mỗi môn phái chỉ nuôi nổi vài nghìn tên là cạn kiệt ngân khố.
* **Virtual Thread (Java 21 Project Loom)**: *Lục Mạch Hư Ảnh (Vô Ảnh Phân Thân)* — Hàng triệu ảo ảnh nhẹ như lông hồng bay lượn trên RAM Heap, gặp tường chắn I/O thì tự ẩn mình nhường đường.
* **Carrier Thread**: *Hắc Phong Toạ Kỵ (Linh Thú Chuyên Chở)* — Thú cưỡi của hệ điều hành, chuyên cõng các Hư Ảnh bay vào điện CPU diện kiến Hoàng Đế.
* **Context Switching**: *Thay Ca Đổi Trận* — Chi phí CPU dừng máy, ghi biên bản bàn giao và đổi vũ khí giữa các đệ tử, gây hao tổn nguyên khí (CPU Thrashing).
* **Thread Pinning**: *Trúng Đinh Định Thân* — Hư Ảnh dùng nhầm phép *Kim Chung Tráo (synchronized)* khi gặp I/O, bị dính chặt mông vào yên Toạ Kỵ, làm tê liệt toàn bộ đàn linh thú.
* **Work-Stealing (ForkJoinPool)**: *Trộm Việc Tương Trợ* — Luồng rảnh rỗi tự động chạy sang nhặt bớt việc ở đuôi hàng của luồng khác về làm giúp để tối ưu 100% công lực.

---

### 2. Cõi Khí Vận (Result Pipeline Realm)
* **Future<V> (Java 5)**: *Chờ Đợi Lệnh Bài (Future.get())* — Kiếm khách cầm lệnh bài đứng chờ bồ câu đưa thư về, phong bế toàn bộ kinh mạch (Blocking wait) không làm được việc gì khác.
* **CompletableFuture (Java 8)**: *Vạn Kiếm Quy Tông (Truyền Âm Trận)* — Kiếm phóng đi bất đồng bộ (`supplyAsync`), tự bọc lửa trên không (`thenApply`), tự ghép đôi điêu (`thenCombine`), gãy kiếm tự về y quán (`exceptionally`), kiếm khách ung dung uống trà không cần đứng đợi.
* **Structured Concurrency (Java 21+)**: *Đồng Tâm Kết Giới* — Mở ngoặc sinh ra đệ tử, đóng ngoặc gom toàn bộ đệ tử lại, cha con cùng tiến cùng thoái, tuyệt đối không bỏ rơi tàn quân mồ côi (Thread Leak).

---

### 3. Cõi Hộ Thể & Thiên Đạo (Safety & Memory Realm)
* **Race Condition**: *Song Hổ Tranh Mồi* — 2 đệ tử cùng lao vào thò tay rút vàng trong 1 chiếc rương (`count++`), kết quả rương nổ tung nát vụn dữ liệu (Data Corruption).
* **Deadlock**: *Tẩu Hỏa Nhập Ma / Song Long Khóa Chết* — Thằng A giữ đao chờ kiếm, thằng B giữ kiếm chờ đao, cả hai đứng nhìn nhau tới chết đói.
* **Synchronized (Java 1.0)**: *Kim Chung Tráo / Thiết Bố Sam* — Khóa nguyên thủy mức linh hồn, tự động thu hồi khi hết chiêu, nhưng không có thời hạn hẹn giờ.
* **ReentrantLock (Java 5 AQS)**: *Cửu Cung Toả Tiên Trận* — Ổ khóa ma thuật linh hoạt, có bùa hẹn giờ (`tryLock`) quá 3 giây không mở được thì rút lui, an toàn tuyệt đối với Virtual Threads.
* **StampedLock (Java 8)**: *Ảo Ảnh Phù Lục (Đọc Lạc Quan)* — Đọc kinh thư không tốn chân khí, đọc xong liếc nhìn tem phù lục có bị rách không để đạt tốc độ xuất chiêu cực hạn.
* **Volatile**: *Chiếu Yêu Kính* — Treo giữa trời, rọi thẳng ánh sáng vào đan điền RAM, cấm tiệt CPU giấu lén dữ liệu trong nội y (L1/L2 Cache).
* **Happens-Before (JMM)**: *Thiên Đạo Quy Tắc* — Luật trời định sẵn thứ tự nhìn thấy chiêu thức giữa các môn phái.
* **False Sharing**: *Chung Phiến Đá 64-byte (Cache Line Bouncing)* — Hai cao thủ ngồi chung một phiến đá hẹp, người này thở mạnh làm người kia rung chuyển té ngã.
