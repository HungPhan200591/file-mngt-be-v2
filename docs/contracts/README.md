# Contracts

Thư mục này là nguồn chuẩn cho contract liên service.

```text
events/<event-type>.md    Event Kafka: producer, consumer, schema, version, retry
openapi/<service>.yaml    REST contract public của service
```

Khi contract chưa tồn tại, feature Design phải tạo nó trước hoặc cùng lượt triển khai. Không dùng Java class được chia sẻ làm tài liệu contract.
