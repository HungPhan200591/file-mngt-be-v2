---
name: review-learning
description: "Điều phối active recall và đánh giá mức nắm kiến thức từ học liệu Backend V2. Dùng khi người dùng muốn xem những chủ đề đã học, chọn một topic để ôn, luyện hỏi-đáp từng câu, nhận gợi ý tăng dần hoặc lưu snapshot tiến độ; không dùng để tạo deep-dive, summary hay question bank mới."
---

# Ôn tập kiến thức đã học

Roadmap là source of truth cho tiến độ học cá nhân; deep-dive, summary và question bank vẫn là owner của kiến thức. Không suy ra người dùng đã nắm một chủ đề chỉ vì artifact tồn tại.

## 1. Nạp đúng phạm vi

1. Đọc `AGENTS.md`, `manual/learning/README.md` và `manual/learning/ADVANCED_MICROSERVICES_STUDY_ROADMAP.md`.
2. Liệt kê bảng **Tiến độ ôn tập cá nhân** trong roadmap, rồi hỏi người dùng chọn topic; nếu chưa từng xác nhận, gọi đó là **học liệu sẵn sàng**, không gọi là “đã học”.
3. Chỉ sau khi đã chọn topic, đọc README của topic và artifact theo ưu tiên: `summary/` → `question-bank/` → deep-dive. Mở code/contract/evidence chỉ khi cần kiểm tra một claim đang gây tranh luận.

## 2. Dẫn một phiên active recall

1. Chốt cấp độ (`FOUNDATION`, `SENIOR`, `ARCHITECT`) và số câu; mặc định `FOUNDATION`, 5 câu.
2. Chọn question chain liên quan, ưu tiên câu hỏi đã có trong question bank. Khi chưa có, tạo câu hỏi chỉ từ artifact đã kiểm chứng và nói rõ đó là câu hỏi tạm thời, không tự thêm vào question bank.
3. Hỏi đúng một câu mỗi lượt, chờ câu trả lời; không tiết lộ đáp án hoặc danh sách keyword trước.
4. Sau mỗi câu trả lời, phản hồi ngắn theo thứ tự: phần đúng → một thiếu sót/misconception quan trọng nhất → kết luận `Vững`, `Đạt`, `Đang hình thành` hoặc `Chưa nắm`.
5. Nếu chưa đạt, đưa tối đa hai gợi ý tăng dần và hỏi lại cùng ý. Gợi ý đầu định hướng mental model; gợi ý sau nêu boundary/keyword, nhưng không phải đáp án hoàn chỉnh.
6. Sau hai lần gợi ý vẫn chưa đạt, đưa đáp án mẫu ngắn, link về artifact owner và chuyển câu tiếp theo. Không coi việc đọc đáp án là trả lời đúng.

## 3. Kết thúc và lưu snapshot

Kết phiên bằng kết quả theo topic: số câu đạt ngay, đạt sau gợi ý, chưa nắm; ba lỗ hổng lớn nhất; và đúng một bước ôn tiếp theo.

Chỉ cập nhật hàng topic tương ứng trong bảng **Tiến độ ôn tập cá nhân** khi người dùng yêu cầu lưu, hoặc đã bật lưu tiến độ cho phiên đó. Ghi snapshot hiện tại (`Mức hiện tại`, `Ôn gần nhất`, `Trọng tâm lần tới`); thay thế giá trị cũ, không append lịch sử hay điểm từng câu.

## Guardrails

- Không chấm dựa vào cách diễn đạt; chấm đúng ownership, cơ chế, failure mode và trade-off cốt lõi.
- Không dùng roadmap như factual source, không tạo claim mới và không cập nhật deep-dive/question bank trong phiên ôn tập.
- Nếu artifact thiếu hoặc mâu thuẫn, nêu rõ giới hạn và đề xuất dùng skill owner để sửa ở một task riêng.
