# ⚡ Structured Logging — Ultra-Short Summary

> 📖 **Muốn đọc Deep-Dive chi tiết?** Bấm vào đây: [02-structured-logging-elk.md](../02-structured-logging-elk.md)

---

## 🧭 1. Dây Chuyền 6 Mắt Xích Logging

```
[SLF4J Interface] ➔ [Logback Engine + Spring Boot 4 ECS JSON] ➔ [File local: logs/*.json]
                                                                        │
                                                             (Logstash tail sincedb)
                                                                        ▼
[Kibana Web UI :18114] ◄── [Elasticsearch DB :18113] ◄── [Logstash Container :18115]
```

---

## ⚡ 2. Keyword Cốt Lõi Nhớ Nhanh (Flashcard)

1. **Spring Boot 4 ECS Formatter**: Định dạng log JSON chuẩn ECS mà **KHÔNG bỏ Logback hay Logstash**.
2. **Decoupled File Shipping**: App ghi file local qua OS Page Cache (`< 1ms`). ELK sập 100% ➔ REST API vẫn `200 OK`.
3. **Log Rolling (`.json.gz`)**: Đủ 10MB/50MB nén `.json.gz` ➔ **Giảm 85% - 90% dung lượng đĩa local**.
4. **Log Retention**: `max-history` (7 - 14 ngày) tự dọn file nén cũ; `total-size-cap` giới hạn tổng GB đĩa.
5. **Logstash `sincedb`**: Con trỏ ngầm lưu vị trí `(Inode, Byte Offset)` đã đọc, không bao giờ đọc lặp lại.
6. **Async Thread Safety**: Tomcat Thread đẩy LogEvent vào RAM RingBuffer (`< 0.1ms`), Async Daemon Thread ghi đĩa ngầm.
7. **Buffer Overflow Safety**:
   - `discardingThreshold`: Đệm đầy 80% tự drop `TRACE/DEBUG/INFO`, giữ `ERROR`.
   - `neverBlock=true`: Đệm đầy 100% vứt log mới, **tuyệt đối không block REST API**.
8. **Zero-Lombok vs `@Slf4j`**: Dự án dùng Java 25 `record` + `private static final Logger LOGGER = LoggerFactory.getLogger(CurrentClass.class);`.
9. **🚨 Hậu quả Quên đổi Class Name**: Kibana in sai `log.logger` ➔ Chẩn đoán nhầm sự cố trên Production.
