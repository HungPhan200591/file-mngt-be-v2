# `media.subject.changed.v1` — RETIRED

Contract này chỉ còn để giữ traceability cho feature lịch sử. Nó không còn là runtime target và không có
compatibility/dual-publish requirement trong study project.

Contract active: [`media.subject.changed.v2`](./media.subject.changed.v2.md).

BT-09D/BT-09E sẽ thay thẳng producer/consumer v1 bằng v2. Qualification environment được reset Kafka topic
và database projection local trước khi chạy scale ladder; không duy trì mixed-version rollout.
