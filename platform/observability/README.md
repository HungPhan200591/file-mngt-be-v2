# Observability

Technical module dùng chung cho các quy ước observability trong Backend V2.

Hiện module sở hữu:

- tên header `X-Correlation-Id` và MDC key `correlationId`;
- validation và sinh correlation ID thống nhất;
- servlet filter tự cấu hình để đưa correlation ID vào response và MDC khi gọi service trực tiếp.

Gateway vẫn là canonical entry point. Vì Gateway đã có filter riêng để chuẩn hóa cả request chuyển tiếp,
auto-filter của module này phải được tắt tại Gateway bằng
`observability.http-correlation.enabled=false`.
