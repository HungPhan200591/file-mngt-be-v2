# Docsify site maintenance

`generate-sidebar.mjs` tạo `_sidebar.md` từ mọi file Markdown trong `manual/` và `docs/`, ở mọi độ sâu. Sidebar giữ nguyên cây thư mục nên vẫn dễ định vị khi tài liệu tăng lên.

Khi deploy, Agent tự chạy `node ./.docsify/generate-sidebar.mjs` ngay trước commit/push. GitHub Actions chỉ upload artifact đã có sidebar đó. Site public mirror toàn bộ repository, trừ dữ liệu nhạy cảm và output local: `.git`, `.env*`, `.idea`, `target`, `node_modules`, `_site`, `logs`, `tmp`.
