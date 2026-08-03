---
name: deep-dive-technical-topic
description: Tạo hoặc sửa tài liệu deep-dive kỹ thuật theo first principles, đi từ mất gốc đến Senior/Architect, có mental model trực quan, cơ chế runtime, failure modes, trade-offs và liên hệ dự án. Dùng khi người dùng muốn học sâu, hiểu bản chất, giải thích từ gốc hoặc thấy tài liệu hiện tại rời rạc/thiếu sơ đồ; không dùng cho summary hay question bank thuần túy.
---

# Deep-dive kỹ thuật từ first principles

## Nạp bằng chứng tối thiểu

1. Đọc tài liệu đích và đúng implementation/config dự án được nhắc đến.
2. Với hành vi phụ thuộc version, dùng `$find-docs`; ưu tiên tài liệu chính thức và ghi version.
3. Phân loại claim khi viết: **bản chất chung**, **hành vi framework**, **cấu hình dự án**, hoặc **suy luận**.
4. Nếu tạo/sửa Mermaid, dùng `$mermaid-styling` trước khi chỉnh diagram.

## Dựng learning spine

Đi theo đúng thứ tự, không nhảy thẳng vào tool:

1. `D0 — Problem`: hệ thống cần giải quyết vấn đề gì nếu chưa có công nghệ này?
2. `D1 — Vocabulary`: đơn vị nhỏ nhất, dữ liệu vào/ra, invariant và ranh giới khái niệm.
3. `D2 — Mechanism`: một request/event đi qua các bước, thread, memory, I/O và network nào.
4. `D3 — Failure`: backpressure, loss/duplicate, retry, durability, security, cost và observability của chính cơ chế.
5. `D4 — Architecture`: khi nào chọn, alternative, trade-off, evolution và cách bảo vệ business boundary.

Giải thích khái niệm trước acronym; mỗi section phải trả lời một câu hỏi rõ ràng.

## Cấu trúc artifact bắt buộc

- Mục tiêu học và prerequisite ngắn.
- “Bản chất trong một câu” cùng keyword spine.
- Mental model tổng thể bằng một diagram; thêm diagram cơ chế/failure chỉ khi quan hệ khó diễn đạt bằng prose.
- Bảng ranh giới component: owns, không owns, input/output, thay thế được bằng gì.
- Luồng runtime từng bước với ví dụ cụ thể.
- Mapping vào dự án bằng file/config thật.
- Failure model và guarantees: phân biệt guarantee, best effort và assumption.
- Decision table: dùng khi nào, không dùng khi nào, alternative và trade-off.
- Misconceptions/red flags, cầu nối phỏng vấn và tài liệu tham khảo.

## Quy tắc chống “hiểu giả”

- Không dùng “luôn”, “không bao giờ”, “100%”, latency hoặc tỷ lệ tiết kiệm nếu chưa có evidence.
- Không suy ra async, exactly-once, durability hay auto-retention chỉ từ tên sản phẩm.
- Tách rõ `default` / `optional configuration` / `configured in this project`.
- Nếu một câu chứa ba khái niệm mới, tách và xây prerequisite trước.
- Dùng ví dụ nhỏ để giải thích, rồi mới mở rộng sang microservice/production.

## Quality gate

- Người mất gốc trả lời được “vì sao tồn tại” và “mỗi component làm gì”.
- Senior giải thích được runtime, failure và trade-off mà không đọc thuộc lòng vendor feature.
- Architect bảo vệ được quyết định và lộ trình evolution.
- Sơ đồ đọc được trong viewport, prose không lặp diagram, mọi claim project-specific có đường dẫn kiểm chứng.
