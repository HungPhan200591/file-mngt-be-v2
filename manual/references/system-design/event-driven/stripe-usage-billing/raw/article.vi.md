# Cách Stripe xây dựng usage-based billing

> Nguồn: [Stripe](https://stripe.com/blog/how-we-built-it-usage-based-billing)  
> Bản dịch đầy đủ theo source capture trong `article.en.md`.

![Hình 1: Header usage-based billing](https://images.stripeassets.com/fzn2n1nzq965/7EExFvCgb0Dp7TTmKd2EC8/f31404de2517772dd0e162a24af02ef4/Usage_based_billing_Header_and_social__1_.png?w=1616&q=80)

Minh họa bởi Álvaro Bernis.

Usage-based billing (UBB) ngày càng phổ biến vì cho khách hàng sự linh hoạt và gắn chi phí với giá trị sử dụng. Stripe nhận thấy nhu cầu lớn với UBB throughput cao, phát hành các nâng cấp gồm credit burndown pricing và năng lực xử lý tới 100.000 event mỗi giây trên mỗi business.

Sản phẩm tập trung vào ba tính năng: revenue ledger chính xác và highly available; xử lý event realtime với throughput cực cao; và hỗ trợ pricing phức tạp, billing chính xác ngay cả khi event đến trễ. Kết hợp ba yêu cầu này khó vì throughput cao gây áp lực lên real-time processing. Kiến trúc cung cấp bài học cho event streaming platform lớn và đáng tin cậy.

## Bài học 1: Async event processing tăng tốc và giảm chi phí nhưng cần developer observability

Stripe muốn tăng throughput 100 lần, giữ availability 99,999%, zero data loss và latency thấp, đồng thời xử lý hàng triệu event mỗi giây với chi phí hợp lý. API Stripe truyền thống authenticate, validate, route, gọi RPC rồi mới chạy business logic qua nhiều server nội bộ theo cách synchronous. Cách này quá chậm và đắt cho event stream.

UBB API đưa event tới edge router để stateless authentication và API validation, sau đó ghi trực tiếp lên event bus. Async processing chuyển event tới Dashboard hoặc billing logic mà không làm chậm stream. Nhược điểm là failure không hiện ra ngay. Stripe giải quyết bằng Dashboard theo dõi processing realtime và webhook thông báo validation failure. Async API chỉ nhanh, tin cậy và tiết kiệm khi đi kèm developer observability tốt hơn.

![Hình 2: Meter events](https://images.stripeassets.com/fzn2n1nzq965/58sKjWagOcVCeyO0W8crdW/9a479a4e24215f3f34c5b8c6edf9c5ec/Meter-events_2x.png?w=1616&q=80)

## Bài học 2: Active-active giảm downtime nhưng cần metadata để reconciliation chính xác

Để xử lý chính xác mà không hy sinh reliability hoặc latency, Stripe chọn Apache Flink vì distributed stream processing, latency thấp và exactly-once guarantee. Flink đôi khi downtime, điều không chấp nhận được với usage tài chính realtime. Stripe xử lý cùng event đồng thời tại hai region theo mô hình active-active.

Active-active giải quyết downtime nhưng tạo bài toán consistency giữa region. Stripe gắn mỗi event metadata chuẩn hóa, gồm timestamp tạo trước khi event đến hai Flink application, event type và source. Metadata giống nhau giúp so sánh hai stream, reconciliation event trễ, duy trì aggregation chính xác và liên tục trong downtime.

## Bài học 3: Tách fast path và slow path để ghép usage với pricing

Stripe biểu diễn pricing như một stream change rồi ghép với event stream của từng customer. Khi hai stream lệch nhau, ví dụ discount retroactive, cần lookback window nhưng không được dừng event stream. Stripe tạo hai pipeline aggregation với tốc độ khác nhau.

Fast path dùng tumbling window 30 giây lưu trong memory. Nó trigger billing alert, bảo đảm event không mất và phản hồi nhanh. Slow path dùng window 5 phút, ghi event xuống disk như transactional ledger. Nó xử lý event trễ/out-of-order và edge case, đồng thời tạo analytics, invoicing data và record cho revenue recognition.

## Hệ thống usage-based billing có thể mở rộng

Stripe báo cáo năng lực 100.000 event/giây/user, P95 dưới 30 giây cho operation nhạy latency và end-to-end khoảng 5 phút từ ingestion tới rated output cho phần lớn use case. Xem [usage-based billing](https://docs.stripe.com/billing/subscriptions/usage-based), [Stripe jobs](https://stripe.com/jobs/search) và [guide](https://stripe.com/lp/usage-based-billing-guide).
