# Under the Hood: Building and open-sourcing RocksDB

> Source: [Engineering at Meta](https://engineering.fb.com/2013/11/21/core-infra/under-the-hood-building-and-open-sourcing-rocksdb/)  
> Published: November 21, 2013

Every time one of Facebook's 1.2 billion users visits the site, applications provide a unique dynamically generated home page and require global, real-time data fetching. Facebook open-sourced [RocksDB](http://rocksdb.org/), an embeddable, persistent key-value store for fast storage.

![Image 1](https://engineering.fb.com/wp-content/uploads/2013/11/Fm3_DADCuha8yG4CAL3TAUJuPQkAAAM.png)

## Why build an embedded database?

Applications traditionally access data through remote procedure calls, which can be slow for real-time products. With flash storage, applications can manage their dataset locally. When requests are served from memory or fast flash, network latency can be comparable to flash latency, making network access potentially twice as slow. Servers also have more CPU cores and storage IOPS, while lock contention and context switches prevent traditional database software from saturating the hardware. New software must be customizable for these hardware trends.

## The vision for RocksDB

RocksDB builds on [LevelDB](https://code.google.com/p/leveldb/) to:

1. Scale across many CPU cores.
2. Use fast storage efficiently.
3. Remain flexible for innovation.
4. Support IO-bound, in-memory and write-once workloads.

## 1. RocksDB scales to run on many CPU cores

RocksDB has simpler semantics than a traditional DBMS; it supports [MVCC](http://en.wikipedia.org/wiki/Multiversion_concurrency_control) only for read-only transactions. It also separates read-only and read-write paths, reducing locks and contention for high concurrency.

## 2. Efficient storage: IOPS, compression and write wear

Flash cards can support very high random-operation rates. RocksDB can run fast enough not to bottleneck such storage. Compared with update-in-place [B-trees](http://en.wikipedia.org/wiki/B-tree), a write-optimized store can improve compression and reduce write amplification, saving storage and flash wear.

## 3. Flexible architecture

RocksDB is manageable and extensible. A merge operator can turn some read-modify-write updates into write-only operations, reducing IO for write-heavy workloads.

## 4. IO-bound, in-memory and write-once workloads

An IO-bound workload is larger than memory and frequently reads storage. An in-memory workload fits in memory but persists changes. A write-once workload mostly inserts keys once. RocksDB was optimized for IO-bound workloads. RocksDB is not a distributed database; it focuses on a high-performance single-node engine.

## Architecture of RocksDB

RocksDB is a C++ library storing arbitrary byte-stream keys and values in sorted sequences. New writes go to new storage locations, and background compaction removes duplicates and processes delete markers. Data uses a [log-structured merge tree](http://en.wikipedia.org/wiki/Log-structured_merge-tree). It supports atomic writes of key sets and forward/backward iteration.

RocksDB has a pluggable architecture. Compression modules such as snappy, zlib and bzip can be replaced. Applications can provide compaction filters, for example an expiry-time filter, custom APIs for write caching, key layouts such as prefix-hash, and custom storage-file formats.

![Image 2](https://engineering.fb.com/wp-content/uploads/2013/11/Flv_DAD2AdyPNKQAACh_YzluPQkAAAM.png)

RocksDB supports level-style and universal-style compaction, trading read, write and space amplification differently. Compactions are multithreaded. It provides incremental online backup and Bloom filters on key subparts to reduce IOPS for range scans.

## Performance

RocksDB can use flash IOPS and was measured faster than LevelDB for random reads, writes and bulk uploads: 10 times faster for pure random writes and bulk upload, and 30 percent faster for pure random reads in the cited tests. LevelDB's single-threaded compaction caused write stalls and high P99 latency for some workloads; mmap introduced read bottlenecks. Lower write amplification helped applications use more flash bandwidth.

## Typical workloads

RocksDB suits low-latency access such as user viewing history/state, spam detection, real-time graph search, real-time Hadoop queries and message queues with many inserts/deletes. The source code is available at [GitHub](http://github.com/facebook/rocksdb), with a [RocksDB Facebook Group](https://www.facebook.com/groups/rocksdb.dev).
