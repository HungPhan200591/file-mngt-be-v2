---
name: study-use-case-scenario
description: Tạo hoặc sửa study card, deep-dive, summary, question bank hay evidence cho UC/SC trong `manual/learning/use-cases`. Dùng khi người dùng gọi UC-xx, SC-xx, scenario nghiệp vụ, Scale & Capacity, workload contract hoặc muốn tổ chức học liệu theo use case; không dùng cho feature ADLC, contract hay code implementation.
---

# Study UC và Scale/Capacity

## Nạp context tối thiểu

1. Đọc `manual/learning/use-cases/README.md`, card UC/SC đích và đúng deep-dive owner liên quan.
2. Chỉ đọc `ADVANCED_MICROSERVICES_STUDY_ROADMAP.md` khi cần prerequisite hoặc thứ tự học; đọc code/contract thật khi ghi claim project-specific.
3. Kiểm tra `git status --short`; giữ nguyên thay đổi sẵn có.

## Chọn owner artifact

- UC study pack: `manual/learning/use-cases/core-flows/uc-<nn>-<slug>/`, gồm `README.md`, rồi đến `summary/` và `question-bank/` khi người dùng yêu cầu; chỉ tạo `01-deep-dive.md` khi giải thích đó thuộc riêng UC.
- SC study pack: `manual/learning/use-cases/scale-capacity/sc-<nn>-<slug>/`, gồm `README.md`, `01-deep-dive.md`, rồi mới đến `summary/` và `question-bank/` khi người dùng yêu cầu.
- Kiến thức tái sử dụng qua nhiều UC/SC: `manual/learning/deep-dive/<topic>/`.
- Card UC/SC chỉ giữ scenario, prerequisite, invariant, evidence, trạng thái và link; không sao chép deep-dive.
- Không đặt một SC trong `deep-dive/scan-service` chỉ vì service đó là owner implementation. Thêm link hai chiều khi cần điều hướng.

## Workflow

1. Chốt ID, scenario, prerequisite và workload contract trước khi viết. Một con số volume không thay thế peak, SLO, retention, resource limit và correctness requirement.
2. Phân loại nội dung: deep-dive giải thích từ first principles; summary chỉ cô đọng từ deep-dive; question bank chỉ sinh từ artifact đã kiểm chứng; evidence phải trỏ test/code/contract thật.
3. Với SC, nêu bottleneck là giả thuyết cho đến khi có baseline. Không tự chọn cache, sharding, Kubernetes, virtual thread hay batch size trước benchmark.
4. Với UC/SC đang `Chờ` hoặc chưa đủ prerequisite, chỉ tạo/sửa card và link học; không tự tạo code, contract hay tuyên bố năng lực đã đạt.
5. Nếu task tạo Mermaid, dùng `$mermaid-styling`. Nếu task trở thành feature/đổi nghiệp vụ, dừng phần đó và route `$adlc-feature-delivery` hoặc `$cross-service-contract`.

## Guardrails

- `manual/` là học liệu, không thay architecture, owner context, contract hay trạng thái triển khai ở `docs/STATUS.md`.
- Không tạo Brief/Design/Plan ADLC, summary hay question bank nếu người dùng chưa yêu cầu.
- Không copy dài từ source code hoặc duplicate kiến thức giữa card và deep-dive; dùng link owner.
- Không chuyển trạng thái UC/SC sang hoàn tất khi thiếu evidence/test. Không ghi latency, throughput hay cấu hình “tối ưu” chưa đo.

## Bàn giao

Kiểm tra ID, prerequisite, link owner, không có nội dung trùng và `git diff --check`. Nêu rõ artifact là study hay source of truth và phần nào chưa được đo/kiểm chứng.
