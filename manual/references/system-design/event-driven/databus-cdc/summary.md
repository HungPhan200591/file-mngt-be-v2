# Summary: Databus

Mẫu cốt lõi: `primary DB -> relay/log -> consumer`; consumer mới hoặc bị tụt dùng `bootstrap snapshot -> replay`.

Áp dụng Backend V2: dùng cho Query projection, DLT replay, rebuild index và xác định projection watermark. Không suy ra Kafka hiện tại đã có infinite lookback hoặc snapshot bootstrap tương đương.
