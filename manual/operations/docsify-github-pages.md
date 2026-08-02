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

`_sidebar.md` được viết tay để chỉ hiện các lối đọc hữu ích: Manual, trạng thái, kiến trúc, contract/E2E và FT013.

Không tự sinh sidebar từ toàn bộ repository: navigation sẽ lộ source/context lịch sử, quá dài và khó dùng trên điện thoại.

## Local preview

Từ root repository:

```powershell
npx docsify-cli serve .
```

Mở URL do lệnh in ra. Local preview và GitHub Pages dùng cùng `index.html`, `_sidebar.md`, `_404.md` và Markdown source.

## Deploy

Push `main` có thay đổi Docsify/public docs sẽ tự chạy workflow `Deploy Docsify to GitHub Pages`.

Muốn chạy ngay bằng GitHub CLI sau khi đã push:

```powershell
gh workflow run deploy-docs.yml --ref main
gh run list --workflow deploy-docs.yml --limit 1
```

Lần đầu cần chọn **Settings → Pages → Source: GitHub Actions** trên GitHub repository. Workflow chỉ upload public docs site đã chọn; không upload toàn repo hoặc `.env`.

## Khi gặp 404

1. Mở URL gốc của site trước, không mở path filesystem trực tiếp.
2. Dùng link hash route `#/...`.
3. Vào Actions kiểm tra run `Deploy Docsify to GitHub Pages` đã xanh.
4. Kiểm tra Pages source là `GitHub Actions`.
5. Deep link cũ không có hash sẽ được `404.html` redirect về Docsify nếu GitHub Pages trả trang 404.
