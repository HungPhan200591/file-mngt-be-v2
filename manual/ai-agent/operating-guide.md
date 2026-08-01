# Guide vận hành với AI Agent

Mục tiêu: luôn để Agent tự tìm đúng nguồn chuẩn, không dán lại lịch sử hoặc toàn bộ rule vào prompt.

## Bắt đầu hoặc tiếp tục session

Nói: `Nạp context Backend V2, rồi <task>.`

Agent đọc router, snapshot trạng thái, architecture summary và chỉ context owner cần thiết. Không yêu cầu Agent đọc toàn bộ `docs/`.

## Làm feature mới

Nói: `Tạo docs ADLC cho <feature>, chưa code.`

Kết quả cần có là Brief, Design và Plan. Chỉ khi `03-plan.md` là `READY` mới yêu cầu: `Triển khai feature <id> theo Plan.`

`03-plan.md` là execution capsule. Nó phải nói rõ owner, file trong scope, bất biến phải giữ và tài liệu chỉ đọc khi cần. Vì vậy lúc code, Agent không phải nạp cả folder feature.

## Sửa một việc cục bộ

Nói: `Sửa <task> ở <service/module>; đọc AGENTS và context owner trước.`

Agent chỉ cần architecture summary và đúng một `apps/<service>/CONTEXT.md`, sau đó mở file owner cùng dependency trực tiếp. Không cần nạp `STATUS.md` hoặc feature docs nếu task không thuộc feature đang làm.

Nếu task chạm port local, Agent phải đọc [ADR-004](./adr/ADR-004-local-port-allocation.md) trước; không dùng port mặc định hoặc đoán theo service.

## Khi thay boundary hoặc contract

Nói: `Dùng cross-service-contract để thiết kế/thay đổi <REST | Kafka | database ownership | migration>.`

Đây là lúc cần đọc contract/ADR liên quan. Không dùng Kafka để thay mọi HTTP call và không truy cập database của service khác.

## Khi sửa rule hoặc tài liệu vận hành

Nói: `Cập nhật governance cho <vấn đề>; giữ tài liệu ngắn và một source of truth.`

Agent dùng skill governance; không tạo thêm wiki, changelog hay rule lặp lại chỉ để “cho đủ”. Nếu skill project-local chưa tự nhận diện, `AGENTS.md` đã có path fallback để Agent đọc trực tiếp.

## Khi bàn giao

Nói thêm: `Handoff kèm source-of-truth audit.`

Agent phải xác nhận architecture, contract, ownership hoặc rule có thực sự đổi không. Nếu có thì cập nhật owner tương ứng trong cùng task; nếu không thì nói rõ không cần cập nhật tài liệu. `docs/STATUS.md` luôn là snapshot hiện tại, không append lịch sử.

Sau đó Agent luôn đề xuất 1–3 việc tiếp theo theo mức ưu tiên. Đây là đề xuất để người dùng chọn, không phải quyền tự làm tiếp ngoài task hiện tại.

## Quy ước ngắn cần nhớ

- Một Agent chính, không dùng sub-agent.
- Không tự chạy Docker, Maven, migration, import hoặc restart nếu chưa được yêu cầu rõ ràng.
- Giữ source file không quá 500 dòng; tách theo trách nhiệm nhưng không vụn.
- Agent không cần được nhắc lại toàn bộ rules mỗi lần: bắt đầu từ `AGENTS.md`, rồi để router chọn đúng context/skill.
