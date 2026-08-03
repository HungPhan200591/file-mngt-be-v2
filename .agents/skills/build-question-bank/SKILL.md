---
name: build-question-bank
description: "Thực hiện tạo mới, cập nhật và duy trì Ngân Hàng Câu Hỏi Phỏng Vấn (Question Bank) theo chuẩn Senior/Architect sau khi đào sâu (deep-dive) bất kỳ chủ đề kỹ thuật nào. Đảm bảo đầy đủ Elevator Pitch, Chuỗi Hỏi-Đáp Keyword, Answer Outline, Trade-offs và Red Flags."
---

# 📚 Build Question Bank Skill

Skill này quy định quy trình chuẩn để xây dựng và bổ sung các **Ngân Hàng Câu Hỏi Phỏng Vấn (Question Bank)** trong thư mục `manual/learning/deep-dive/<topic>/question-bank/`.

---

## 🎯 Cấu Trúc Bắt Buộc Của Mỗi Câu Hỏi

Mỗi câu hỏi phỏng vấn trong Ngân hàng câu hỏi BẮT BUỘC phải có **đầy đủ 8 thành phần** sau:

```markdown
### [PREFIX]-[TOPIC]-[ID] — `[LEVEL: FOUNDATION | SENIOR | ARCHITECT]`
**Question:** [Nội dung câu hỏi phỏng vấn sát thực tế]<br>
**Target depth:** `[D1-D4]` · **Interview likelihood:** `[HIGH | MEDIUM]` · **Question type:** `[COMMON_CORE | COMMON_SCENARIO | PROJECT_APPLICATION | ARCHITECTURE_EVOLUTION]`<br>
**Interviewer evaluates:** [Nhà tuyển dụng đánh giá năng lực gì ở ứng viên]<br>

⚡ **Trả lời siêu ngắn (Elevator Pitch)**:
> *"[1-2 câu trả lời cô đọng, sắc bén nhất nắm trọn bản chất vấn đề]"*

🧠 **Chuỗi Hỏi - Đáp Keyword (Memory Flashcard Chain)**:
- ❓ **[Câu hỏi phụ 1]?** ➔ 💡 **[Đáp án ngắn kèm **Keyword nổi bật**]**.
- ❓ **[Câu hỏi phụ 2]?** ➔ 💡 **[Đáp án ngắn kèm **Keyword nổi bật**]**.
- ❓ **[Câu hỏi phụ 3]?** ➔ 💡 **[Đáp án ngắn kèm **Keyword nổi bật**]**.
- 🔑 **Keyword cốt lõi cần nhớ**: **[Keyword 1] — [Keyword 2] — [Keyword 3]**.

**Answer outline:**
- **[Ý chính 1]**: [Phân tích chi tiết kèm ví dụ code/config/mô hình].
- **[Ý chính 2]**: [Phân tích nguyên lý hoạt động ngầm/threading/memory/I-O].
- **[Ý chính 3]**: [Cách triển khai thực tế trong Backend V2].<br>
**Required trade-offs:** [Những đánh đổi kiến trúc bắt buộc phải chấp nhận].<br>
**Follow-up ladder:** [Các câu hỏi đào sâu tiếp theo nhà tuyển dụng có thể hỏi xoáy].<br>
**Red flags:** [Các câu trả lời hời hợt, sai bản chất hoặc ngộ nhận cần tránh].
```

---

## 📐 Quy Trình 4 Bước Tạo Ngân Hàng Câu Hỏi

1. **Khảo sát Chủ đề & Xác định Ma Trận Coverage**:
   - Phân bổ số lượng câu hỏi theo 3 cấp độ: `FOUNDATION` (Nền tảng), `SENIOR` (Chuyên sâu), `ARCHITECT` (Thiết kế hệ thống).
   - Đảm bảo ma trận được cập nhật chính xác ở đầu file.

2. **Soạn Thảo Nội Dung Đa Tầng**:
   - Viết **Elevator Pitch**: Cô đọng trong 1-2 câu, dùng từ ngữ đắt giá.
   - Viết **Memory Flashcard Chain**: Tạo 3-5 câu hỏi - đáp nhanh dạng Thẻ nhớ (Flashcard) có bọc `**Keyword**` đậm để ứng viên dễ học thuộc trong 30 giây.
   - Viết **Answer Outline**: Trình bày chi tiết, có chuyên môn sâu, dẫn chứng bằng cấu hình hoặc nguyên lý HĐH/JVM/Network.

3. **Gắn Mã Định Danh (ID Prefix Standard)**:
   - Observability Overview: `OBS-OVERVIEW-xxx`
   - Observability Metrics: `OBS-METRIC-xxx`
   - Observability Logging: `OBS-LOG-xxx`
   - Observability Tracing: `OBS-TRACE-xxx`
   - Observability Alerting/Dashboard: `OBS-DASH-xxx`
   - Transactional Outbox: `OUTBOX-xxx`
   - CQRS Read Projection: `CQRS-xxx`
   - Event-Driven Kafka: `KAFKA-xxx`

4. **Audit Kiểm Tra Trước Khi Bàn Giao**:
   - Kiểm tra 100% câu hỏi đã có đủ **⚡ Elevator Pitch** và **🧠 Flashcard Chain** chưa.
   - Đảm bảo giữ đúng tiếng Việt có dấu, giữ nguyên thuật ngữ kỹ thuật tiếng Anh.
