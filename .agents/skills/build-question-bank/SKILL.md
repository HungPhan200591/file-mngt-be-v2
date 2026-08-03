---
name: build-question-bank
description: Tạo, sửa và audit question bank phỏng vấn kỹ thuật từ deep-dive đã kiểm chứng. Dùng khi người dùng yêu cầu ngân hàng câu hỏi, question chain, flashcard hỏi-đáp nhanh, mock interview hoặc luyện phỏng vấn Foundation/Senior/Architect; không dùng để viết deep-dive hay summary.
---

# Xây dựng question bank

## Nạp nguồn

1. Đọc deep-dive của đúng chủ đề; đọc summary nếu có để lấy keyword spine, không dùng summary làm nguồn cho claim mới.
2. Đọc implementation/config dự án khi câu hỏi có loại `PROJECT_APPLICATION`.
3. Nếu claim phụ thuộc version library/framework, dùng `$find-docs` trước khi viết đáp án.
4. Ghi rõ ba loại kiến thức: bản chất chung, hành vi framework có điều kiện, và cấu hình thực tế của dự án.

## Thiết kế coverage trước khi viết

Tạo ma trận theo concept và độ sâu `FOUNDATION`, `SENIOR`, `ARCHITECT`; mỗi level phải có coverage thật. Nhóm câu hỏi theo các chuỗi có thứ tự:

`WHY → WHAT → HOW → FAILURE → TRADE-OFF → PROJECT → EVOLUTION`

- Tạo 4–7 question chain, mỗi chain có 5–8 cặp hỏi-đáp nhanh.
- Mỗi đáp án nhanh tối đa hai câu, bôi đậm 1–3 keyword có sức gợi nhớ.
- Câu sau phải đào sâu trực tiếp từ câu trước; không gom các flashcard rời rạc chỉ vì cùng chủ đề.
- Tách “default”, “đã cấu hình trong dự án” và “có thể cấu hình”; cấm tuyệt đối hóa kiểu “không bao giờ mất”, “luôn async”, “100% non-blocking”.

## Anchor interview questions

Chọn 6–12 câu đại diện, không lặp lại toàn bộ flashcard. Mỗi anchor bắt buộc có:

```markdown
### <ID> — `<LEVEL>` · `<TYPE>`
**Question:** ...
**Interviewer evaluates:** ...
**Trả lời 30 giây:** ...
**Answer spine:** 3–5 ý theo thứ tự lập luận.
**Project evidence:** file/config hoặc “không áp dụng”.
**Trade-offs:** ...
**Follow-up ladder:** 2–4 câu sâu dần.
**Red flags:** ...
```

`TYPE` dùng một trong `COMMON_CORE`, `COMMON_SCENARIO`, `PROJECT_APPLICATION`, `ARCHITECTURE_EVOLUTION`.

## Retrieval practice

- Cuối file thêm 8–15 câu tự kiểm tra không kèm đáp án ngay bên dưới; link ngược về chain/anchor để tự chấm.
- Với câu khó, thêm “keyword cứu hộ” thay vì chép lại đáp án.
- Ưu tiên khả năng nói thành lời: có câu 30 giây, 2 phút và tình huống phản biện trade-off.

## Audit trước bàn giao

- Đếm lại matrix từ nội dung thật; ID duy nhất và tăng ổn định.
- Mọi concept cốt lõi có ít nhất một rapid chain và một anchor hoặc nêu rõ lý do.
- Đáp án không mâu thuẫn deep-dive/summary; link không mồ côi.
- Xóa câu trùng, con số không có nguồn, slogan tuyệt đối và chi tiết không giúp phân biệt năng lực ứng viên.
