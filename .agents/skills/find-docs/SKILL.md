---
name: find-docs
description: Tra cứu tài liệu hiện hành cho library, framework, SDK, API hoặc CLI trước khi viết code phụ thuộc version, cấu hình, migration, setup hay xử lý lỗi. Dùng để giữ tương thích router `$find-docs` của Backend V2; skill này ủy quyền tra cứu sang `$context7-mcp`.
---

# Find Docs

1. Gọi `$context7-mcp` và tuân theo workflow của skill đó: resolve library trước, rồi tra đúng một vấn đề cụ thể.
2. Dùng kết quả documentation hiện hành để quyết định API, cấu hình, compatibility, migration hoặc error handling.
3. Không dùng cho pure domain logic, đổi tên, format, refactor không đổi hành vi framework hoặc review business logic.
4. Nếu `$context7-mcp` không khả dụng, báo rõ blocker; không suy đoán API từ kiến thức cũ.
