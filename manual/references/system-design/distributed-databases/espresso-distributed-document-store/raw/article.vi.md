# Giới thiệu Espresso - distributed document store mới của LinkedIn

> Nguồn: [LinkedIn Engineering](https://engineering.linkedin.com/espresso/introducing-espresso-linkedins-hot-new-distributed-document-store)  
> Tác giả: Aditya Auradkar, Tom Quiggle  
> Ngày xuất bản: 21/01/2015

## Bối cảnh và yêu cầu

Espresso là cơ sở dữ liệu NoSQL online, phân tán và fault-tolerant của LinkedIn, phục vụ các ứng dụng như Member Profile, InMail, Homepage và mobile applications. Bài viết mô tả nhu cầu thay thế các RDBMS cũ và Voldemort bằng một primary store có strong consistency, read-after-write, timeline-consistent change capture, mở rộng ngang, secondary index nhất quán, transaction trong một partition, schema evolution zero-downtime, fault tolerance và bulk ingest từ HDFS.

## Mô hình dữ liệu

Mô hình có cấu trúc `database -> table -> collection -> document`. Database chứa các table và dùng chung partitioning/physical resources. Table chứa các document đồng kiểu, có composite key; key đầu là partitioning key. Partial key xác định một collection; full key xác định một document. Database/table schema dùng JSON, document schema dùng Avro. Document được lưu nội bộ dưới dạng Avro-serialized binary blob; `indexType` yêu cầu tạo secondary index.

## Kiến trúc

- `Router` là stateless HTTP proxy, hash partition key, tra routing table cục bộ và chuyển request tới storage node; multi-get dùng scatter-gather.
- `Storage Node` lưu partition, primary data và metadata; dùng pluggable storage engine, production dùng MySQL/InnoDB; duy trì secondary index đồng bộ với base data, local transaction, ordered replication commit log, consistency checks, validation và backup.
- `Helix` tính `IdealState` từ state model, so sánh với `ExternalView` và phát state transition. Mỗi partition có một master, có thể có nhiều slave; replica không đặt cùng node; slave được promote khi master lỗi.
- `Databus` đọc transaction log theo commit order để phát change stream cho search index/cache và replication đa data center.
- `Data Replicator` là Databus consumer, batch theo partition, checkpoint trong ZooKeeper và replay từ checkpoint khi node thay thế.
- `Snapshot Service` khôi phục backup, sinh Avro files theo table để ETL vào HDFS và bootstrap Databus consumer bị mất event.

## API và tính đúng đắn

REST API dùng `PUT` để ghi/thay thế document hoặc transaction nhiều document trong collection, `POST` cho partial update hoặc autoincrement trailing key, `DELETE` để xóa và `GET` để đọc document/collection/multi-get. Conditional operations dùng `Last-Modified` và `ETag` cho read-modify-write và cache.

Mỗi mutation thành công nhận một `64 bit system change number (SCN)` tăng dần theo partition. SCN gồm generation và sequence; mastership transition tăng generation và reset sequence. Nhờ vậy các event cùng transaction có cùng SCN và replication giữ được thứ tự theo partition.

Schema nằm trong ZooKeeper và được version hóa. Schema evolution tuân theo Avro; field mới optional cho phép document version cũ được promote. Production vô hiệu hóa schema deletion để tránh xóa nhầm dataset.

## Fault tolerance và backup

ZooKeeper ephemeral node giúp phát hiện node lỗi; Helix loại node khỏi ExternalView, tính IdealState mới và promote một slave thành master. Có một cửa sổ write unavailability khi partition chưa có master, nhưng router có thể đọc từ slave để duy trì partial availability. Backup được tạo theo partition, chứa SCN, có thể dùng restore node, bootstrap cluster hoặc expansion; sau restore, node catch up các update sau SCN của backup.

## Attribution và giới hạn capture

Bản dịch này là bản dịch faithful theo cấu trúc và nội dung kỹ thuật của bài viết public, giữ nguyên code/identifier/API/URL. Các gist code trong trang gốc không được extractor trả về đầy đủ; cần mở source URL để xem code gốc. Hình ảnh được giữ dưới dạng source references trong `article.en.md`; chưa tạo bản sao binary local.
