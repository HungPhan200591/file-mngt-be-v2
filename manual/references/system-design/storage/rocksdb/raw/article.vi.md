# RocksDB: xây dựng và open source embedded storage engine

> Nguồn: [Engineering at Meta](https://engineering.fb.com/2013/11/21/core-infra/under-the-hood-building-and-open-sourcing-rocksdb/)  
> Xuất bản: 21/11/2013

Mỗi lần một trong 1,2 tỷ người dùng Facebook truy cập, nhiều application phải tạo homepage riêng và fetch dữ liệu realtime trên phạm vi toàn cầu. Facebook open source [RocksDB](http://rocksdb.org/), một persistent key-value store dạng embedded cho storage nhanh.

![Hình 1](https://engineering.fb.com/wp-content/uploads/2013/11/Fm3_DADCuha8yG4CAL3TAUJuPQkAAAM.png)

## Vì sao xây embedded database?

Application thường truy cập dữ liệu qua remote procedure call, có thể chậm với sản phẩm realtime. Với flash storage, application có thể quản lý dataset local. Khi request được phục vụ từ memory hoặc flash nhanh, network latency có thể tương đương flash latency, khiến truy cập qua network chậm gấp đôi. Server cũng có nhiều CPU core và IOPS hơn, nhưng lock contention/context switch làm database truyền thống không tận dụng hết hardware. Storage software mới cần tùy biến theo hardware.

## Tầm nhìn RocksDB

RocksDB xây trên [LevelDB](https://code.google.com/p/leveldb/) với mục tiêu:

1. Scale trên nhiều CPU core.
2. Dùng fast storage hiệu quả.
3. Linh hoạt để đổi mới.
4. Hỗ trợ workload IO-bound, in-memory và write-once.

## 1. Scale trên nhiều CPU core

RocksDB có semantics đơn giản hơn DBMS truyền thống; hỗ trợ [MVCC](http://en.wikipedia.org/wiki/Multiversion_concurrency_control) chỉ cho read-only transaction. Nó tách read-only path và read-write path để giảm lock/contention trong high-concurrency workload.

## 2. Storage hiệu quả: IOPS, compression và write wear

Flash card có thể xử lý lượng random operation rất lớn. RocksDB đủ nhanh để không trở thành bottleneck. So với [B-tree](http://en.wikipedia.org/wiki/B-tree) update-in-place, write-optimized store cải thiện compression và giảm write amplification, từ đó giảm dung lượng và hao mòn flash.

## 3. Kiến trúc linh hoạt

RocksDB dễ mở rộng. Merge operator biến một số read-modify-write thành write-only, giảm IO trong workload write-heavy.

## 4. Workload IO-bound, in-memory và write-once

IO-bound lớn hơn memory và thường đọc storage. In-memory vừa trong memory nhưng vẫn persist thay đổi. Write-once chủ yếu insert key một lần. RocksDB được tối ưu trước hết cho IO-bound. RocksDB không phải distributed database; nó tập trung vào single-node engine hiệu năng cao.

## Kiến trúc RocksDB

RocksDB là C++ library lưu key/value dạng byte stream tùy ý theo sorted sequence. Write mới đi tới vị trí mới; background compaction loại duplicate và xử lý delete marker. Dữ liệu dùng [log-structured merge tree](http://en.wikipedia.org/wiki/Log-structured_merge-tree), hỗ trợ atomic write một tập key và iteration xuôi/ngược.

Kiến trúc RocksDB có thể plugin. Có thể thay compression module như snappy, zlib, bzip; thêm compaction filter như expiry-time; tạo API cache write; dùng key layout như prefix-hash; và thay storage-file format.

![Hình 2](https://engineering.fb.com/wp-content/uploads/2013/11/Flv_DAD2AdyPNKQAACh_YzluPQkAAAM.png)

RocksDB có level-style và universal-style compaction, tạo trade-off khác nhau giữa read, write và space amplification. Compaction chạy multithread. RocksDB có incremental online backup và Bloom filter trên một phần key để giảm IOPS cho range scan.

## Performance

RocksDB tận dụng flash IOPS và trong benchmark của bài nhanh hơn LevelDB ở random read/write và bulk upload: nhanh hơn 10 lần ở pure random write và bulk upload, 30% ở pure random read. Single-threaded compaction của LevelDB gây write stall và P99 latency cao trong một số workload; mmap gây bottleneck khi đọc. Giảm write amplification giúp tận dụng nhiều bandwidth flash hơn.

## Workload phù hợp

RocksDB phù hợp low-latency access như viewing history/state, spam detection, real-time graph search, query Hadoop realtime và message queue nhiều insert/delete. Source code ở [GitHub](http://github.com/facebook/rocksdb), cộng đồng tại [RocksDB Facebook Group](https://www.facebook.com/groups/rocksdb.dev).
