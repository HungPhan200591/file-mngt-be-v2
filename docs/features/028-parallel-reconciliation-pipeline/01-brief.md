# 028 Parallel reconciliation pipeline

Owner: `scan-service`

## Vấn đề

Scan 1 triệu file vào staging rất nhanh (discovery dùng `COPY` bulk-load, segment
500k), nhưng pha reconciliation (đối soát changed + tạo proposal/issue + commit
inventory) chậm hơn nhiều lần. Historical experiment tăng `business-chunk-size`
từ 15k lên 100k không
cải thiện do bottleneck không nằm ở số lần commit mà nằm ở xử lý tuần tự trong
mỗi chunk.

Ba nguyên nhân gốc:

1. **Analyze tuần tự trên 1 thread**: Mỗi item phải chạy regex parse, semantic
   extract, JSON serialize qua Jackson và policy evaluate — tất cả trên single
   thread. Với 1 triệu file, tổng CPU time ước tính 100–200 giây.
2. **JPA `saveAll()` cho proposals/issues**: `ScanChunkCommitter` gọi
   `proposals.saveAll()` + `flush()` qua JPA `EntityManager`, tạo overhead
   persistence context nặng. Inventory đã dùng JDBC `batchUpdate()` trực tiếp nhưng
   proposals/issues thì chưa.
3. **Không có pipeline overlap**: Đọc page → analyze → commit xếp hàng tuần tự;
   không tận dụng thời gian I/O chờ DB để CPU analyze page tiếp.

## Mục tiêu và acceptance criteria

- Pha analyze chạy song song trên nhiều virtual thread, chia partition từ danh sách
  changed items. Mức parallelism cấu hình được qua `scan.reconciliation-parallelism`,
  mặc định 8.
- Insert proposals và issues dùng JDBC `batchUpdate()` trực tiếp, bỏ JPA
  `saveAll()` trong luồng commit reconciliation chunk. Tận dụng
  `reWriteBatchedInserts=true` đã có trên datasource.
- Kết quả scan (proposal count, issue count, inventory state) phải giống hệt
  luồng tuần tự cũ với cùng dữ liệu đầu vào.
- `ScanProgress` counter vẫn chính xác sau parallel aggregate.
- Lease fence, checkpoint, liveness và SSE progress không bị ảnh hưởng; chỉ phần
  analyze CPU-bound chạy song song, phần commit DB vẫn single-thread trong
  `@Transactional(REQUIRES_NEW)`.
- `DIFF_PAGE_SIZE=25k` là effective page size hiện tại để giới hạn memory và
  transaction. `business-chunk-size=100k` vẫn là upper bound cấu hình; nó không
  có nghĩa mỗi page runtime chứa 100k item và không thay đổi contract.
- Không thay đổi schema DB, REST API, Kafka event hay SSE contract.

## Ngoài phạm vi

- Không pipeline overlap (prefetch page tiếp khi đang commit page hiện tại) — đánh
  giá sau benchmark, không nằm trong feature này.
- Không parallel commit (vẫn single transaction per chunk).
- Không thay đổi discovery, staging COPY, materialization diff hay finalization.
- Không thay đổi JPA repository cho các luồng đọc (query/pagination trên REST API).
- Không code, build, test hay chạy service trong bước lập feature này.

## Câu hỏi/rủi ro mở

- Không còn quyết định nghiệp vụ/kiến trúc chặn Plan. Khi triển khai cần benchmark
  trước/sau trên tập dữ liệu 1 triệu file để xác nhận mức cải thiện thực tế.
- Nếu parallel analyze + JDBC batch vẫn chưa đủ nhanh, bước tiếp theo là pipeline
  overlap hoặc tăng parallelism; quyết định sau benchmark.
