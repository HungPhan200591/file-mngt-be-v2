# Migration storage của Messenger để tối ưu performance

> Nguồn: [Engineering at Meta](https://engineering.fb.com/2018/06/26/core-infra/migrating-messenger-storage-to-optimize-performance/)  
> Xuất bản: 26/06/2018

Hơn một tỷ người dùng Facebook Messenger để chia sẻ text, photo, video và nhiều nội dung khác. Messenger chuyển từ sản phẩm giống email, nơi message chờ trong inbox, thành hệ thống liên lạc mobile-first và realtime. Monolithic service ban đầu được tách thành read-through cache cho query; [Iris](https://code.facebook.com/posts/820258981365363/building-mobile-first-infrastructure-for-messenger/) để queue write tới subscriber như storage và device; và storage service lưu message history.

Storage service được hiện đại hóa qua ba thay đổi:

* thiết kế và đơn giản hóa schema, tạo source-of-truth index từ dữ liệu cũ và định nghĩa invariant;
* chuyển từ HBase sang [MyRocks](https://code.facebook.com/posts/190251048047090/myrocks-a-space-and-write-optimized-mysql-database/), tích hợp RocksDB làm MySQL storage engine;
* chuyển từ spinning disk sang flash trên [Lightning Server SKU](https://code.facebook.com/posts/989638804458007/introducing-lightning-a-flexible-nvme-jbof/).

Kết quả là resiliency và latency tốt hơn, giảm 90% storage và thêm khả năng mobile content search mà không disruption/downtime. Để bảo đảm mọi Messenger user được tính đủ, nhóm phải xây dựng hai migration flow.

## Thách thức migration ở quy mô lớn

HBase từng phục vụ Messenger tốt, nhưng MyRocks cho phép dùng flash thay spinning disk. MySQL replication topology cũng phù hợp hơn với data center của Facebook, giảm số physical replica mà vẫn cải thiện availability và disaster recovery.

Migration phải giữ Messenger chạy cho hơn một tỷ account. Đọc toàn bộ historical data trên HBase là I/O-bound; làm quá nhanh sẽ làm HBase chậm và gây lỗi cho user. Business user có thể mở nhiều chat window liên tục, nên code phải hỗ trợ product change trên cả hệ thống cũ và mới trong lúc chuyển account.

Phải chuyển toàn bộ petabytes dữ liệu của từng account. Vì schema thay đổi, migration phải parse legacy data lộn xộn, xử lý corner case và conflict, nhưng user vẫn phải thấy đúng message, video và photo cũ. Đồng thời nhóm phát triển database mới, thiết kế Lightning hardware và sửa lỗi software, kernel, firmware, thậm chí power path.

Normal flow xử lý 99,9% account; buffered migration flow xử lý phần khó. Nhóm validation dữ liệu, chuẩn bị revert plan và chạy accounting job để chắc chắn không bỏ sót ai trước khi tắt hệ thống cũ.

![Chart 1: Migration workflow](https://engineering.fb.com/wp-content/uploads/2018/06/statemachine-new-code.png)

## Normal migration

Single-user migrator giả định account không có write trong lúc migration. State machine và monitoring tool bảo vệ giả định này. Account có một trong ba static state: `not-migrated`, `double-writing`, `done`, hoặc dynamic state khi đang migration.

Khi bắt đầu, hệ thống ghi lại last data position ở storage service cũ và Iris, rồi copy dữ liệu sang hệ thống mới. Khi copy xong, hệ thống kiểm tra source position có thay đổi không. Nếu không đổi, write được chuyển sang MyRocks và account vào `double-writing`. Nếu có đổi, migration thất bại, dữ liệu MyRocks được cleanup và job sau sẽ retry account.

Trong `double-writing`, migrator chạy data validation và API validation. Data validation so sánh HBase với MyRocks. API validation đọc từ cả hai hệ thống và so sánh response để client đọc liền mạch. Trước `done`, workflow xác nhận migration thành công. Revert plan có thể đưa account về `not-migrated`, chuyển read về hệ thống cũ và xóa dữ liệu hệ thống mới.

![Chart 2: Normal migration flow](https://engineering.fb.com/wp-content/uploads/2018/06/NormalMigration_New_site2.jpg)

## Buffered migration flow

Một số account không có quiet period, chẳng hạn business lớn chạy Messenger bot, hoặc có kích thước bất thường. Buffered flow đặt cutoff `migration start`, snapshot account, copy snapshot vào buffer tier rồi migrate buffer sang MyRocks. Trong lúc đó write tới MyRocks được queue ở Iris, nơi có thể queue message nhiều tuần. Khi snapshot migrate xong, account vào `double-writing`; MyRocks nhận write trở lại, drain queue và catch up.

Buffer có thể là HBase tier riêng dùng schema cũ. Với account cực lớn, server SSD riêng chạy embedded RocksDB giúp migration nhanh hơn.

![Chart 3: Buffered migration flow](https://engineering.fb.com/wp-content/uploads/2018/06/BufferedMigration_new-site.png)

## Migration ở quy mô lớn

Meta dùng [Bistro framework](https://facebook.github.io/bistro/) để parallelize migration job, schedule, track progress, log/analyze progress và throttle khi service có vấn đề. Khi account đạt `done`, account mới chỉ tạo ở hệ thống mới, write tới HBase dừng từng cluster và accounting job xác nhận mọi account trong HBase đã được migrate. Cuối cùng đạt 100% migration.

## Lợi ích

Normal flow migrate 99,9% account trong hai tuần; phần còn lại qua buffered flow trong hai tuần tiếp theo. Schema đơn giản hơn giảm disk usage. [Zstandard](https://github.com/facebook/zstd) và giảm replication factor từ sáu xuống ba giúp giảm 90% storage mà không mất dữ liệu.

MyRocks có disaster recovery tự động hơn; chuyển data center không còn cần supervisor thao tác thủ công. Kết hợp Lightning flash, read latency thấp hơn HBase 50 lần. Migration cũng mở đường cho mobile message search dựa trên search infrastructure của Facebook xây trên MySQL. Hệ thống mới tạo nền tảng cho các cải tiến Messenger tiếp theo.
