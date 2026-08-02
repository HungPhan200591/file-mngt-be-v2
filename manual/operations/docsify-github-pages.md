# Docsify và GitHub Pages

Tài liệu này dành cho chủ dự án. Nó không phải context mặc định hay rule tự nạp của AI Agent.

## URL và cách mở đúng

Site Docsify chạy tại:

```text
https://hungphan200591.github.io/file_mngt_microservice/
```

Luôn ưu tiên link dạng hash route, ví dụ:

```text
https://hungphan200591.github.io/file_mngt_microservice/#/manual/README.md
```

Hash route giúp Docsify xử lý navigation trong browser thay vì để GitHub Pages tìm folder HTML không tồn tại.

## Sidebar

`.docsify/generate-sidebar.mjs` sinh `_sidebar.md` từ mọi file Markdown có trong repository, ở mọi độ sâu. Sidebar giữ cây thư mục thay vì một danh sách phẳng, nên vẫn định vị được nhanh khi đọc trên điện thoại.

Không sửa tay `_sidebar.md`. Khi deploy, AI Agent tự chạy script trước commit/push; người dùng không phải chạy tay.

## Local preview

Từ root repository:

```powershell
npx docsify-cli serve .
```

Mở URL do lệnh in ra. Local preview và GitHub Pages dùng cùng `index.html`, `_sidebar.md` và Markdown source.

## Deploy

Mọi push lên `main` đều chạy workflow `Deploy Docsify to GitHub Pages`. Workflow mirror toàn bộ repository và dùng `_sidebar.md` đã được Agent sinh trước push.

Muốn chạy ngay bằng GitHub CLI sau khi đã push:

```powershell
gh workflow run deploy-docs.yml --ref main
gh run list --workflow deploy-docs.yml --limit 1
```

Lần đầu cần chọn **Settings → Pages → Source: GitHub Actions** trên GitHub repository. Artifact loại `.git`, `.env*`, `.idea`, `target/`, `node_modules/`, `_site/`, `logs/`, `tmp/` và `*.iml` để không đưa bí mật hoặc output cục bộ lên Pages.

## Khi gặp 404

1. Mở URL gốc của site trước, không mở path filesystem trực tiếp.
2. Dùng link hash route `#/...`.
3. Vào Actions kiểm tra run `Deploy Docsify to GitHub Pages` đã xanh.
4. Kiểm tra Pages source là `GitHub Actions`.
