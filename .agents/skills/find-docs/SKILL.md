---
name: find-docs
description: Tra cứu tài liệu hiện hành cho library, framework, SDK, API hoặc CLI trước khi viết code phụ thuộc version, cấu hình, migration, setup hay xử lý lỗi. Ưu tiên Context7 MCP; khi MCP không khả dụng thì chuyển sang Context7 CLI theo global rule.
---

# Find Docs

1. Khi `$context7-mcp` khả dụng, resolve library trước rồi tra đúng một vấn đề cụ thể.
2. Khi `$context7-mcp` không khả dụng, chuyển sang workflow Context7 CLI được quy định ở global rule; skill này không lặp lại câu lệnh, auth hoặc sandbox policy.
3. Dùng kết quả documentation hiện hành để quyết định API, cấu hình, compatibility, migration hoặc error handling; không suy đoán từ kiến thức cũ.
4. Không dùng cho pure domain logic, đổi tên, format, refactor không đổi hành vi framework hoặc review business logic.
