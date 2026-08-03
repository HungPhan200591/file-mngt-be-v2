# 📚 Deep-Dive: CQRS Lite, Read Projection & Dual-Store Search

> **Phạm vi**: Đi sâu vào mô hình **CQRS Lite**, cách xây dựng **Read Model / Projection** đồng bộ qua Kafka, chiến lược **Dual-Store Search (Elasticsearch Fast Hit + PostgreSQL/Redis Hydration)** và cơ chế **Data Reconciliation & Cache Eviction**.

---

## 🗺️ Bản Đồ Điều Hướng Tài Liệu

| STT | Chủ Đề Chi Tiết | Mô Tả Nội Dung | Ngân Hàng Câu Hỏi |
| :---: | :--- | :--- | :--- |
| **00** | [**CQRS Lite & Eventual Consistency**](00-overview.md) | Tách biệt Command/Query Side, khoảng trễ Eventual Consistency và kiến trúc Read Model | [**Hỏi & Đáp CQRS Overview**](question-bank/00-cqrs-projection-questions.md#1-cqrs-lite--eventual-consistency) |
| **01** | [**Fast Hit & Hydration Pattern**](01-elasticsearch-redis-hydration.md) | Elasticsearch Fast Hit ID search, Hydration từ PostgreSQL/Redis và fallback safe response | [**Hỏi & Đáp Fast Hit & Hydration**](question-bank/00-cqrs-projection-questions.md#2-dual-store-search--hydration) |
| **02** | [**Reconciliation & Cache Eviction**](02-reconciliation-cache-aside.md) | Thuật toán đối soát state, Cache-Aside pattern và chiến lược Eviction khi có Event | [**Hỏi & Đáp Reconciliation & Cache**](question-bank/00-cqrs-projection-questions.md#3-reconciliation--cache-eviction) |

---

## 🎯 Mục Tiêu Kiến Thức Cần Đạt Được

1. **Hiểu rõ lý do dùng CQRS Lite**: Tại sao không JOIN trực tiếp Write DB mà phải tách ra Read Projection chuyên biệt.
2. **Nắm vững mô hình Fast Hit & Hydration**: Tách biệt Search Engine (Elasticsearch) chỉ làm nhiệm vụ filter ID, còn DB/Redis làm nhiệm vụ trả full metadata.
3. **Thành thạo Data Reconciliation**: Cách Query Service so sánh snapshot cũ/mới từ Kafka Event để tự động thêm/sửa/xóa asset mà không bị sai lệch dữ liệu.
4. **Tự tin Phỏng vấn Senior/Architect**: Trả lời trôi chảy các câu hỏi về Eventual Consistency lag, Stale Cache, Elasticsearch Index re-sync và Trade-offs.
