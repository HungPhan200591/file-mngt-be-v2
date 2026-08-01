# Local infrastructure

P0 dùng `compose.yaml` với host port theo [ADR-004](../../docs/adr/ADR-004-local-port-allocation.md); override qua `.env` sao chép từ `.env.example`. PostgreSQL tạo đúng ba database/user: `catalog_db/catalog_user`, `scan_db/scan_user`, `query_db/query_user`. Volume được Docker quản lý (`postgres-data`, `kafka-data`, `redis-data`); không xóa volume khi chưa có yêu cầu rõ ràng.

Khi bootstrap, thư mục này sở hữu Docker Compose đã pin version cho PostgreSQL, Kafka KRaft và Redis. ELK (Elasticsearch, Logstash, Kibana) nằm trong profile `observability` ở phase quan sát; ba image dùng cùng version pin, không dùng `latest`.

Elasticsearch chứa hai logical data set tách biệt: logs data stream cho ELK và media search index do Query sở hữu. Không dùng Elasticsearch làm canonical database. Không commit secret thật. Mọi port, volume và health check phải được ghi vào compose cùng tài liệu local tương ứng.
