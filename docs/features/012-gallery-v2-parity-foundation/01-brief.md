# 012 Gallery V2 parity foundation — Brief

## Mục tiêu

Tạo nền tảng `Gallery V2` tách hẳn core UI/data state khỏi Gallery Web V1, nhưng giữ trải nghiệm thị giác và interaction V1 làm chuẩn tham chiếu.

## Acceptance criteria

- Gallery V2 có entry/context riêng, chỉ gọi Gateway V2 khi bắt đầu tích hợp dữ liệu.
- Không import renderer, state, API client hoặc business logic từ `gallery-web/`.
- Chỉ tái sử dụng Shared UI primitives/token; V1 là visual/behavior reference khi cần đối chiếu.
- Có parity contract ngắn, owner map và baseline state cần kiểm tra; Agent không phải nạp Gallery V1 theo mặc định.
- Có lộ trình backfill/dual-run để chuyển dần, không thay default hoặc làm hỏng V1.

## Ngoài phạm vi

- Không thay Gallery Web V1, không cutover default entry.
- Không ép Query/Catalog DTO về shape V1 và không thêm adapter compatibility V1.
- Không backfill dữ liệu production/local thật trong feature foundation.
