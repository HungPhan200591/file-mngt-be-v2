# 015 Nginx media delivery V2 tách biệt — Plan

Status: DONE
Design: [02-design.md](./02-design.md)

## Execution capsule

- Owner: `infra/compose` cho Nginx runtime; `gateway-service` cho CORS browser ingress.
- Scope/files: `infra/nginx/nginx.conf`, `infra/compose/compose.yaml`, `.env.example`, Compose README, ADR-004/005, Gateway HTTP contract, Gateway CORS source/test/context và `docs/STATUS.md`.
- Must preserve: V1 repository/container/port `8888` không đổi; logical locator/path `/files/<drive>:/...` giữ nguyên; mount media read-only; không thêm proxy, database, REST/Kafka business contract, migration hoặc frontend V1.
- Read on demand: ADR-004/005, Gateway context/contract, Compose README, official Nginx Docker/core-module docs qua `$find-docs`.

## Bước triển khai

1. Mở rộng ADR-004 với `18119` cho Nginx media V2 và thay ADR-005 từ tái sử dụng V1 sang container/config/URL V2 độc lập.
2. Thêm Nginx config riêng trong `infra/nginx/` cùng service `nginx-media`, container name, image version pin, port/env và read-only bind mount trong Compose V2.
3. Cập nhật CORS contract, Gateway implementation và integration test sang origin V2; cập nhật Gateway context vì invariant owner đổi.
4. Cập nhật `.env.example`, Compose README và STATUS; ghi kết quả kiểm tra tĩnh vào Plan rồi đặt status `DONE`.

## Kiểm tra

- Static: `git diff --check`, `docker compose --env-file .env.example -f infra/compose/compose.yaml config`, kiểm tra `nginx.conf` chỉ mount/read static root V2, image không dùng `latest` và port khớp ADR-004.
- Unit/integration: Gateway routing integration test xác nhận CORS trả origin `http://localhost:18119`.
- Runtime (chỉ khi được phép): khởi động Compose V2, gọi `GET`, `HEAD` và `Range` vào fixture qua `18119`; xác nhận V1 `8888` và container V2 chạy độc lập.

## Rollout và rollback

- Rollout: tạo `.env`, chạy Compose V2; browser/frontend V2 dùng origin `18119` trước khi kích hoạt direct-delivery migration.
- Rollback: dừng riêng service `nginx-media` hoặc bỏ cấu hình V2; không ảnh hưởng V1, data, Kafka hay các service V2 khác. Với client đang cần URL V1, chỉ đổi deployment URL khi feature migration riêng được triển khai.

## Tài liệu cần cập nhật

- ADR-004/005, Gateway HTTP contract, Gateway context, Compose README, `.env.example`, `docs/STATUS.md` và evidence Plan.
- Không cập nhật OpenAPI hay event contract vì business contract không đổi.

## Implementation handoff — 2026-08-03

- Đã thêm `infra/nginx/nginx.conf` và service Compose `nginx-media` độc lập, tên container `file-mngt-v2-nginx-media`, image pin `nginx:1.30.4-alpine`, port host `18119` và chỉ các bind mount media read-only.
- Đã đổi Gateway CORS, Gateway HTTP contract và owner context sang `localhost`/`127.0.0.1:18119`; origin V1 `8888` không còn được allow-list trong V2.
- Đã cập nhật ADR-004 mở rộng dải host port đến `18119` và ADR-005 xác nhận Nginx V2 không dùng chung container/config/port với V1.
- Static evidence đã pass: `git diff --check`, `docker compose --env-file .env.example -f infra/compose/compose.yaml config`, port `18119` không đang listen và kiểm tra source xác nhận mount chỉ đọc/config V2 riêng.
- Chưa chạy Gateway Maven integration test vì workspace hiện chỉ có `corretto-22.0.2`; quy tắc dự án bắt buộc Maven dùng IntelliJ Project SDK `corretto-25`. Không khởi động Docker Compose hoặc Nginx runtime.
