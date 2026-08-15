# Claims

- Espresso dùng hierarchical model `database -> table -> collection -> document` và Avro cho document schema.
- Router hash partition key và hỗ trợ multi-get bằng scatter-gather.
- Storage node dùng MySQL/InnoDB trong các deployment được bài viết mô tả; MySQL binlog hỗ trợ replication và CDC.
- Secondary indexes được cập nhật synchronous với base data.
- Helix duy trì một master và các slave cho mỗi partition, promote slave khi master lỗi.
- Databus truyền source transactions theo commit order tới downstream consumers.
- Data Replicator checkpoint tiến độ trong ZooKeeper và replay từ checkpoint sau node failure.
- SCN là 64-bit clock theo partition, gồm generation và sequence.
- Schema được version hóa trong ZooKeeper; schema deletion bị disable trong production.
- Backup theo partition chứa SCN và hỗ trợ restore/catch-up.

## Scope và caveat

Các claim trên là mô tả của bài LinkedIn Engineering xuất bản 2015, không phải cam kết về mọi phiên bản Espresso hiện nay. Bài không chứng minh latency/SLO, không mô tả đầy đủ consistency giữa data center và không cung cấp benchmark độc lập.
