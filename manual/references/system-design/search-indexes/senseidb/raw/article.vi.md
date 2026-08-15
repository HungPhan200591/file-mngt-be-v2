# Giới thiệu SenseiDB 1.0: database semi-structured phân tán, realtime, mã nguồn mở

> Nguồn: [LinkedIn Engineering](https://engineering.linkedin.com/open-source/introducing-senseidb-10-open-source-distributed-realtime-semi-structured-database)  
> Bản dịch đầy đủ theo source capture trong `article.en.md`.

Tác giả giới thiệu version 1.0.0 của [SenseiDB](http://senseidb.com/) tới cộng đồng mã nguồn mở. Sensei là database phân tán, elastic, realtime và semi-structured.

**[Xem SenseiDB trên Github!](https://github.com/linkedin/sensei)**

Bài viết trình bày Sensei làm gì, kiến trúc phía sau và hướng phát triển của dự án.

## Sensei là gì?

![Hình 1: Logo SenseiDB](https://content.linkedin.com/content/dam/engineering/en-us/blog/migrated/sensei_black_0.jpg)

Sensei là hệ thống dữ liệu phân tán được xây dựng để hỗ trợ nhiều sáng kiến sản phẩm của LinkedIn, gồm faceted search realtime trong [Signal](http://www.linkedin.com/signal/) và news feed/tab trên [Homepage](http://www.linkedin.com/). Nó là nền tảng cho hạ tầng search và data của LinkedIn.

Sensei vừa là search engine vừa là database. Nó được thiết kế để query và điều hướng qua các document gồm (a) unstructured text và (b) structured metadata được định dạng rõ ràng.

## Tính năng

Các tính năng và điểm khác biệt của Sensei:

* Nhận insert/update với tốc độ cao trong khi vẫn duy trì [query performance cao](http://senseidb.com/performance.html).
* Hỗ trợ query phức tạp qua [query language](http://senseidb.com/bql.html) (BQL) và [REST/JSON API](http://senseidb.com/client-rest.html).
* Streaming update từ nhiều [Gateway](http://senseidb.github.com/sensei/indexing-gateway.html) như JDBC, JMS và [Kafka](http://incubator.apache.org/kafka/).
* Bootstrap từ [Hadoop](http://hadoop.apache.org/), ví dụ Map-Reduce job để batch build index và đẩy vào Sensei cluster.
* Cho phép plugin logic faceting tùy biến/phức tạp, chẳng hạn social graph.

## Kiến trúc

![Hình 2: Kiến trúc Sensei](https://content.linkedin.com/content/dam/engineering/en-us/blog/migrated/sensei-architect.png)

### Inserts

Khác nhiều data system, Sensei nhận dữ liệu từ ordered and versioned data stream gọi là [gateway](http://senseidb.github.com/sensei/indexing-gateway.html). Tại LinkedIn, một số stream mà Sensei nhận gồm [Kafka](http://incubator.apache.org/kafka/) và Databus, công nghệ dùng để stream dữ liệu từ database.

Sensei dựa vào external data stream để cung cấp atomicity và isolation guarantee; theo một nghĩa, commit log được externalize. Thiết kế này cho phép tối ưu update rate, đồng thời cung cấp eventual consistency giữa các replica mà không cần quorum.

Chi tiết xem [architecture overview](http://senseidb.com/overview.html) và [clustering](http://senseidb.com/cluster.html).

### Queries

Execution engine của Sensei tối ưu cho dataset rất lớn và hỗ trợ nhiều loại query:

* `get/getAll`, ví dụ key-value retrieval;
* full-text search;
* structured, SQL-like select;
* aggregation, ví dụ facet counting và group-by.

Ngoài [REST/JSON API](http://senseidb.github.com/sensei/clients.html), Sensei hỗ trợ query language dạng SQL gọi là [BQL](http://senseidb.github.com/sensei/bql.html).

## Những gì Sensei KHÔNG làm

So với các data system khác, Sensei không hỗ trợ:

* Sensei không relational. Giống nhiều NoSQL system, dữ liệu được denormalize và không hỗ trợ JOIN.
* Sensei không transactional. Sensei cung cấp durability và eventual consistency nhưng không hỗ trợ full transactional insert model, ví dụ rollback.

## Hướng phát triển

Các hướng được dự kiến cho Sensei:

* relevance toolkit;
* aggregation và field collapsing;
* nested document structure;
* dynamic schema;
* online data rebalancing;
* data import/export;
* inter-cluster Map-Reduce.

## Tham gia

Để tìm hiểu và đóng góp, xem [SenseiDB project page](http://senseidb.com/), [source code](https://github.com/linkedin/sensei), [LinkedIn group](http://www.linkedin.com/groups/SenseiDB-4264313), [mailing list](http://groups.google.com/group/sensei-search) và IRC `irc.webchat.org`, channel `#sensei-search`.
