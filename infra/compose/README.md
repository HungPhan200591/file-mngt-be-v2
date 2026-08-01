# Local infrastructure

Khi bootstrap, thư mục này sở hữu Docker Compose đã pin version cho PostgreSQL, Kafka KRaft và Redis. ELK (Elasticsearch, Logstash, Kibana) nằm trong profile `observability` ở phase quan sát; ba image dùng cùng version pin, không dùng `latest`.

Elasticsearch chứa hai logical data set tách biệt: logs data stream cho ELK và media search index do Query sở hữu. Không dùng Elasticsearch làm canonical database. Không commit secret thật. Mọi port, volume và health check phải được ghi vào compose cùng tài liệu local tương ứng.
