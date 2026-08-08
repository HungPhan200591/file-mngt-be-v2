# Contracts

Thư mục này là nguồn chuẩn cho contract liên service.

```text
events/<event-type>.md    Event Kafka: producer, consumer, schema, version, retry
http/<contract>.md        Contract HTTP cross-cutting qua Gateway
openapi/<boundary>.yaml   REST contract public hoặc internal do service owner công bố
```

Khi contract chưa tồn tại, feature Design phải tạo nó trước hoặc cùng lượt triển khai. Không dùng Java class được chia sẻ làm tài liệu contract.
