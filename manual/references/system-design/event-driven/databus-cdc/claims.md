# Claims

- CDC nên phát thay đổi theo commit order và transaction boundary.
- Consumer mới cần snapshot nhất quán trước khi replay stream.
- Consumer lag cần cơ chế bootstrap/consolidated snapshot.
- CDC transport và downstream projection là hai trách nhiệm khác nhau.

Caveat: đây là mô tả Databus năm 2013, không phải benchmark cho Kafka/Backend V2.
