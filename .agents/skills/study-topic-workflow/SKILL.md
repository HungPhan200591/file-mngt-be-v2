---
name: study-topic-workflow
description: Điều phối trọn bộ workflow học một chủ đề kỹ thuật gồm deep-dive từ first principles, summary cô đọng và question bank phỏng vấn có chain. Dùng khi người dùng yêu cầu deep-dive rồi tạo/cập nhật toàn bộ study pack, muốn học từ cơ bản đến Senior, hoặc muốn repair đồng bộ nhiều artifact học tập; không dùng khi chỉ sửa một artifact đơn lẻ.
---

# Điều phối study pack

## Thứ tự bắt buộc

1. Dùng `$deep-dive-technical-topic` để tạo/sửa deep-dive và chốt factual source trước.
2. Dùng `$distill-study-summary` từ bản deep-dive cuối cùng; summary không được tự sinh claim mới.
3. Dùng `$build-question-bank` từ deep-dive + summary cuối cùng; xây chain trước anchor questions.
4. Audit chéo cả ba artifact và index/README trực tiếp liên quan nếu đã tồn tại.

Không viết ba file độc lập song song vì correction ở deep-dive phải chảy xuống hai artifact còn lại.

## Contract giữa artifacts

- **Deep-dive** sở hữu explanation, evidence, mechanism, failure model và trade-off.
- **Summary** sở hữu compression, keyword spine, decision rules và interview answer ngắn.
- **Question bank** sở hữu retrieval practice, question chain, follow-up và red flags.
- Nội dung dùng chung phải link về owner thay vì copy đoạn dài.

## Vòng lặp khi người dùng hỏi bổ sung

1. Xác định câu hỏi làm thay đổi fact, mental model hay chỉ thêm ví dụ.
2. Cập nhật deep-dive trước nếu fact/mental model đổi.
3. Propagate chỉ keyword/answer/chain bị ảnh hưởng sang summary và question bank trong cùng task.
4. Xóa claim cũ bị thay thế; không append lịch sử hỏi đáp rời rạc vào cuối file.

## Completion gate

- Learning ladder phủ `WHY → WHAT → HOW → FAILURE → TRADE-OFF → PROJECT → EVOLUTION`.
- Có ít nhất một mental model trực quan; diagram dùng `$mermaid-styling`.
- `default`, `optional`, `project-configured` được phân biệt nhất quán.
- Summary không nhiều rác; question bank có nhiều rapid chain nhưng anchor không trùng lặp.
- Link hai chiều hợp lệ, matrix đếm đúng, không có TODO hay claim tuyệt đối thiếu bằng chứng.
