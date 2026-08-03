---
name: add-glossary-term
description: "Thực hiện thêm thuật ngữ mới vào Từ điển IT Glossary (NoteRepo) một cách chuẩn hóa. Đảm bảo cập nhật đồng thời cả file SSOT tóm tắt (d:\\Study\\Project\\NoteRepo\\glossary\\it-glossary.md) và file chi tiết tương ứng trong thư mục details/."
---

# 📖 Add Glossary Term Skill

Skill này quy định quy trình chuẩn để thêm mới thuật ngữ chuyên ngành IT vào hệ thống Từ điển **IT Glossary (NoteRepo)**.

---

## 🎯 Vị Trí Lưu Trữ SSOT

- **File Tóm Tắt SSOT**: `d:\Study\Project\NoteRepo\glossary\it-glossary.md`
- **Thư Mục File Chi Tiết**: `d:\Study\Project\NoteRepo\glossary\details\`
  1. `it-glossary-distributed-systems.md` — Hệ Phân Tán (CAP, Consistency, Consensus, Saga, Outbox, CQRS...)
  2. `it-glossary-concurrency.md` — Đồng Thời & Khóa (CAS, Deadlock, Lock, ThreadLocal, Atomic...)
  3. `it-glossary-database-caching.md` — Cơ Sở Dữ Liệu & Caching (Index, Normalization, Cache-Aside, Eviction...)
  4. `it-glossary-messaging-stream.md` — Hàng Đợi & Dòng Dữ Liệu (Kafka, Consumer, Partition, DLT, At-Least-Once...)
  5. `it-glossary-infrastructure-performance.md` — Hạ Tầng & Hiệu Năng (Telemetry, Prometheus, ECS, Log Rotation, Page Cache...)
  6. `it-glossary-design-architecture.md` — Thiết Kế & Kiến Trúc (SOLID, Hexagonal, Clean Code, Microservices...)

---

## 📐 Quy Trình 3 Bước Thêm Thuật Ngữ Mới

### Bước 1: Xác Định Nhóm Nghiệp Vụ & File Chi Tiết Tương Ứng
Xác định thuật ngữ mới thuộc 1 trong 6 nhóm nghiệp vụ trên.

### Bước 2: Cập Nhật Bảng Tóm Tắt Tại `it-glossary.md`
Thêm 1 dòng vào bảng tương ứng trong `it-glossary.md`. Định dạng dòng:

```markdown
| [**<Term_Name>**](./details/<detail-file>.md#<index>-<slug>) | <Nghĩa_Tiếng_Việt> | <Giải_thích_cô_động_1_dòng> | <Loại: Issue | Solution | Principle | Architecture | Metric> |
```
*Lưu ý*:
- `<index>`: Số thứ tự nối tiếp trong file chi tiết.
- `<slug>`: Tên slug viết chữ thường, nối bằng dấu gạch ngang `-` (ví dụ: `#16-telemetry`).

### Bước 3: Cập Nhật Nội Dung Chi Tiết Trong `details/<detail-file>.md`
Thêm mục chi tiết vào cuối file chi tiết tương ứng với cấu trúc chuẩn:

```markdown
---

### <index>. <Term_Name>
- **Phiên âm**: `/<pronunciation>/`
- **Định nghĩa**: [Giải thích bản chất kỹ thuật trong 2-3 câu tiếng Việt dễ hiểu]
- **Tóm tắt 1 dòng**: *[Cụm từ chốt hạ bản chất]*
- **Ví dụ thực tế**: [Ví dụ gắn liền với Spring Boot, PostgreSQL, Kafka, Prometheus, ELK, hoặc Docker trong dự án]
```

---

## ✅ Ví Dụ Mẫu Áp Dụng (Term: Telemetry)

1. **Thêm vào `it-glossary.md`**:
```markdown
| [**Telemetry**](./details/it-glossary-infrastructure-performance.md#16-telemetry) | Dữ liệu đo đạc từ xa | Thu thập tự động Metrics, Logs và Traces để giám sát hệ thống | Metric |
```

2. **Thêm vào `details/it-glossary-infrastructure-performance.md`**:
```markdown
---

### 16. Telemetry
- **Phiên âm**: `/təˈlemətri/`
- **Định nghĩa**: Telemetry là quá trình tự động thu thập, đo đạc và truyền tải các dữ liệu giám sát (Metrics, Logs, Traces) từ các microservice về hệ thống quản lý trung tâm (Prometheus, ELK, Grafana).
- **Tóm tắt 1 dòng**: *Dữ liệu đo đạc sức khỏe từ xa của hệ thống.*
- **Ví dụ thực tế**: Spring Boot app xuất metric qua `/actuator/prometheus` và xuất JSON log chuẩn ECS chính là phát phát tín hiệu telemetry.
```
