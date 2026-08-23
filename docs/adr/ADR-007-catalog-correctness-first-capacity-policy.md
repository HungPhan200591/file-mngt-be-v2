# ADR-007: Catalog correctness-first capacity policy

Status: ACCEPTED  
Date: 2026-08-23

## Context

FT-059/061 tạo được Catalog pipeline có durable completion shard, retry/failure isolation, final broker-ack
boundary và targeted correctness evidence. FT-060–062 lần lượt kiểm tra bounded writer scaling, shared ingest
fence và immutable target mapping nhưng local single-host PostgreSQL physical 1M vẫn không đạt mục tiêu 90–120
giây. Tiếp tục micro-optimize query không còn là chiến lược có bounded exit.

Hard deadline 120 giây của FT-058 đồng thời là liveness safety và performance gate. Khi người dùng chấp nhận
pipeline ổn định nhưng chậm hơn, giữ deadline này sẽ block operation hợp lệ trước khi nó có cơ hội hội tụ.

## Decision

- Chọn FT-061 stable mode làm Catalog correctness baseline; production consumer concurrency mặc định giữ `1`.
- Catalog 1M/120s không còn là release blocker để mở BT-09E. Catalog throughput và end-to-end
  `QUERY_DB_READY` giữ trạng thái `UNQUALIFIED` cho tới khi có workload/deployment qualification riêng.
- Combined 25K/250K/1M trở thành diagnostic/qualification tự chọn, không phải gate bắt buộc cho functional
  delivery.
- Durable operation deadline vẫn hữu hạn để phát hiện operation treo, nhưng đổi safety ceiling mặc định từ 120
  giây thành 30 phút. Migration V27 gia hạn một lần cho operation V57/V59 đang non-terminal khi deploy.
- Retry exhaustion, DLT, exact cardinality, shard equality, idempotency và final broker acknowledgement vẫn là
  correctness gates bắt buộc.
- Dừng chuỗi FT tối ưu local sau FT-062. Chỉ mở lại performance work khi có thay đổi capacity/deployment/SLO
  rõ ràng, không mở candidate SQL kế tiếp theo kiểu thử-sai. Backlog owner là
  [TD-023](../TECHNICAL_DEBT.md#backlog-đang-mở).

## Alternatives

- **Giữ 120 giây và tiếp tục tối ưu:** không chọn vì FT-060–062 đã có exit evidence nhưng vẫn fail physical gate.
- **Bỏ deadline hoàn toàn:** không chọn vì operation treo sẽ giữ trạng thái non-terminal vô hạn và làm mất tín
  hiệu vận hành.
- **Tăng concurrency mặc định:** không chọn vì bốn upsert workers đã scale âm và shared DB vẫn tranh CPU/I/O/WAL.
- **Tách database/resource ngay:** chưa chọn vì chưa có production workload/cost evidence; đây là lựa chọn capacity
  có thể đánh giá sau, không phải điều kiện tiếp tục functional pipeline.

## Consequences

- Operation lớn có thể hoàn tất chậm hơn 120 giây thay vì bị watchdog block sớm.
- UI/operation API phải biểu diễn trạng thái async thực tế; không hứa thời gian hoàn tất khi chưa qualified.
- BT-09E Query bulk projection được phép bắt đầu trên stable Catalog contract.
- Local benchmark số đẹp không được dùng làm production SLO. Nếu cần SLO mới, phải đo end-to-end trên deployment
  đại diện và ban hành decision thay thế ADR này.
