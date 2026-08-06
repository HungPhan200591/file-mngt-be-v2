# Learning hub — Senior Java / Microservices

`manual/learning/` là không gian học tập gắn với Backend V2, không thay thế architecture, contract, ADR hay code. Khi có khác biệt, ưu tiên [`docs/`](../../docs/), `apps/*/CONTEXT.md` và implementation hiện tại.

## Bắt đầu ở đâu

1. Đọc [System Primer](./system-primer/README.md) để nắm domain, ownership và luồng dữ liệu.
2. Theo [roadmap Senior Java/Microservices](./ADVANCED_MICROSERVICES_STUDY_ROADMAP.md) theo đúng dependency.
3. Từ roadmap, mở đúng UC/SC study pack và làm đến hết study pack trước khi chuyển bài.

## Tài liệu theo loại

- [Technical glossary](./technical-glossary.md): thuật ngữ ngắn, không thay deep-dive.
- [`deep-dive/`](./deep-dive/): giải thích cơ chế, failure model và trade-off có evidence.
- [`use-cases/`](./use-cases/): UC/SC study pack; thứ tự, trạng thái và dependency do roadmap sở hữu.
- [`system-primer/`](./system-primer/README.md): nền tảng đọc dự án theo ngôn ngữ nghiệp vụ.

Mỗi chủ đề mới dùng cấu trúc `deep-dive/<topic>/`, với `summary/` và `question-bank/` bên trong. Use case chỉ liên kết tới các artifact này, không sao chép nội dung dài.
