# Use case study hub

Mỗi thư mục con là một bài toán nghiệp vụ lớn, không phải một danh sách công nghệ. Card use case trả lời: dữ liệu đi đâu, ai sở hữu, điều gì có thể hỏng và Senior Java cần bảo vệ trade-off nào.

## Quy ước structure

```text
manual/learning/
├── system-primer/                 # Bối cảnh chung, đọc trước
├── deep-dive/<topic>/             # Owner: factual explanation
│   ├── summary/                   # Owner: ôn nhanh từ deep-dive
│   └── question-bank/             # Owner: retrieval practice/interview
└── use-cases/
    ├── core-flows/
    │   └── uc-<nn>-<scenario>/    # README, summary/, question-bank/
    └── scale-capacity/
        └── sc-<nn>-<scenario>/    # README, deep-dive, summary/, question-bank/
```

Không tạo một bản deep-dive, summary hoặc question bank riêng trong thư mục use case nếu nó đã thuộc một topic dùng chung. Card chỉ liên kết về owner để tránh hai bản kiến thức lệch nhau.

## Backlog theo thứ tự học

| ID | Scenario | Trạng thái | Artifact hiện có / cần chốt |
| --- | --- | --- | --- |
| [UC-01](./core-flows/uc-01-scan-to-catalog-canonical-ingestion/README.md) | Scan → review → Catalog canonical ingestion | Đang học | Scan + Outbox deep-dive; cần audit evidence, summary và question bank liên use case |
| UC-02 | Catalog → Query projection correctness | Chờ UC-01 | CQRS deep-dive + event contract; tạo card khi bắt đầu |
| UC-03 | Query search, cache và reconciliation | Chờ UC-02 | CQRS deep-dive; tách performance evidence khỏi UC-02 |
| UC-04 | Worker processing → Catalog → Query convergence | Chờ FT013 | Tạo sau khi bắt đầu FT013 |
| UC-05 | Observability và performance E2E | Sau core flow | Observability deep-dive hiện có; bổ sung evidence theo luồng E2E |
| UC-06 | Import V1, replay và đối soát | Deferred | Chỉ tạo khi Phase 7 bắt đầu |

`Đang học` nghĩa là có scope hiện tại, chưa khẳng định mọi artifact đã đủ. `Chờ` nghĩa là không tự tạo code hay nội dung chi tiết trước prerequisite. Trạng thái triển khai chính thức luôn xem ở [`docs/STATUS.md`](../../../docs/STATUS.md).

## Scale & Capacity Track

Sau khi một vertical slice đúng ở quy mô nhỏ, dùng [Scale & Capacity Track](./scale-capacity/README.md) để học bài toán volume/load tương ứng. Track hiện đã định nghĩa các lab Scan 1 triệu entry, Import 1 triệu record, Catalog/Query đến hàng trăm triệu hoặc 1 tỷ record, Kafka replay/backlog và Worker queue quy mô lớn; chưa tạo deep-dive chi tiết trước prerequisite.
