# Open source Databus: hệ thống change data capture độ trễ thấp của LinkedIn

> Nguồn: [LinkedIn Engineering](https://engineering.linkedin.com/data-replication/open-sourcing-databus-linkedins-low-latency-change-data-capture-system)  
> Đồng tác giả: Sunil Nagaraj, Shirshanka Das, Kapil Surlaker  
> Xuất bản: 26/02/2013

LinkedIn công bố bản open source của Databus, một hệ thống real-time change data capture. Databus được phát triển từ năm 2005 và phiên bản mới nhất đã chạy production tại LinkedIn từ năm 2011. Source code có tại [GitHub repository](https://github.com/linkedin/databus).

## Databus là gì?

LinkedIn có nhiều hệ thống storage và serving chuyên biệt. Primary OLTP data store nhận các write và một phần read từ user. Các hệ thống khác phục vụ query phức tạp hoặc tăng tốc kết quả bằng cache. Ví dụ, search query được phục vụ bởi search index và index này phải liên tục cập nhật từ primary database.

Do đó cần một cơ chế change capture đáng tin cậy và transactionally consistent, đưa thay đổi từ primary data source tới các derived data system. Databus được xây dựng cho nhu cầu đó và là một phần của data processing pipeline tại LinkedIn. Transport layer cung cấp end-to-end latency tính bằng milliseconds, throughput hàng nghìn change event mỗi giây trên mỗi server, infinite lookback và rich subscription.

![Hình 1](https://content.linkedin.com/content/dam/engineering/en-us/blog/migrated/databus-usecases.jpg)

Search Index và Read Replica là các Databus consumer thông qua client library. Khi primary OLTP database có write, relay kết nối với database sẽ lấy change vào relay. Consumer nằm trong search index hoặc cache lấy event từ relay hoặc bootstrap rồi cập nhật index/cache. Nhờ đó downstream giữ được trạng thái gần với source database.

## Databus hoạt động thế nào?

Các tính năng chính:

* **Source-independent:** hỗ trợ CDC từ nhiều nguồn như Oracle và MySQL. Oracle adapter có trong bản open source; MySQL adapter được dự kiến open source sau.
* **Scalable và highly available:** mở rộng tới hàng nghìn consumer và transactional data source mà vẫn high availability.
* **Transactional in-order delivery:** giữ transaction guarantee của source và gửi change event theo nhóm transaction, đúng source commit order.
* **Low latency và rich subscription:** event đến consumer trong milliseconds sau khi source tạo change; consumer có thể lấy partition cụ thể bằng server-side filtering.
* **Infinite lookback:** consumer có thể tạo downstream copy, ví dụ search index mới, mà không tạo thêm tải lên primary OLTP database; cơ chế này cũng hỗ trợ consumer bị tụt xa.

![Hình 2](http://s3.amazonaws.com/snaprojects/databus/databus-as-a-service.png)

Databus gồm relay, bootstrap service và client library. Relay lấy committed change từ source database và lưu event trong high-performance log store. Bootstrap Service duy trì moving snapshot của source bằng cách định kỳ áp dụng change stream từ Relay. Application dùng Client Library để đọc stream từ Relay hoặc Bootstrap và xử lý event trong Consumer thông qua callback API.

Consumer chạy nhanh đọc event từ relay. Nếu consumer tụt đến mức event cần đọc không còn trong relay log, consumer nhận consolidated snapshot của các change kể từ change cuối cùng đã xử lý. Consumer mới chưa có bản copy dataset nhận snapshot nhất quán tại một thời điểm từ Bootstrap Service, rồi tiếp tục catch-up từ relay.

## Dùng thử

Người dùng có thể tải và thử [Databus](https://github.com/linkedin/databus). Databus đã chạy production nhiều năm và hỗ trợ primary data processing pipeline quan trọng của LinkedIn. Các liên kết gồm [quick start guide](https://github.com/linkedin/databus/wiki), [source](https://github.com/linkedin/databus) và [discussion group](http://groups.google.com/group/databus-linkedin).

Chủ đề: data replication, change data capture, Distributed Systems, CDC và Open Source.
