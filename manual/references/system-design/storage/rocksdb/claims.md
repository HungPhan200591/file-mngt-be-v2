# Claims

- LSM tree đổi write amplification và compaction cost để tối ưu write throughput.
- Compaction style ảnh hưởng read, write và space amplification.
- Bloom filter có thể giảm IOPS cho range/key lookup.
- RocksDB là single-node embedded engine, không tự cung cấp distributed consistency.

Caveat: benchmark trong bài phụ thuộc hardware/workload lịch sử và không đại diện cho Backend V2.
