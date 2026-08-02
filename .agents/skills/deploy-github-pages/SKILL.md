---
name: deploy-github-pages
description: Deploy Docsify documentation site lên GitHub Pages khi người dùng yêu cầu rõ preview/deploy/publish docs. Dùng cho `index.html`, sidebar curated, GitHub Actions Pages workflow hoặc kiểm tra trang đã deploy; không dùng khi chỉ sửa Markdown bình thường.
---

# Deploy Docsify lên GitHub Pages

## Nạp tối thiểu

1. Đọc `AGENTS.md`, `manual/README.md`, `index.html`, `_sidebar.md` và `.github/workflows/deploy-docs.yml`.
2. Chỉ đọc các Markdown được người dùng yêu cầu sửa; `manual/` không phải context mặc định.
3. Không autogenerate sidebar. `_sidebar.md` là navigation curated, chỉ chứa lối đọc hữu ích.

## Kiểm tra trước deploy

1. Kiểm tra `git status --short` và scope thay đổi; không stage `.env`, `target/`, file local hay source không liên quan.
2. Kiểm tra link nội bộ của sidebar/README, `git diff --check`, tồn tại `index.html`, `_sidebar.md`, `_404.md`, `404.html` và `.nojekyll`.
3. Khi người dùng yêu cầu local preview, chạy Docsify từ repository root; không build Java/Docker chỉ để xem docs.

## Commit và publish

1. Chỉ commit/push khi người dùng yêu cầu rõ; stage chính xác file tài liệu/config được duyệt, không dùng `git add .`.
2. Push `main`; workflow tự deploy khi thay đổi thuộc public docs scope.
3. Nếu người dùng yêu cầu kích hoạt/kiểm tra ngay và `gh` có đăng nhập:

```powershell
gh workflow run deploy-docs.yml --ref main
gh run list --workflow deploy-docs.yml --limit 1
```

4. Khi Pages chưa từng được cấu hình, báo người dùng đặt **Settings → Pages → Source: GitHub Actions** một lần; sau đó mới trigger workflow. Không tự thay đổi repository settings nếu chưa được người dùng cho phép.
5. Báo URL Pages, run ID/kết quả và nếu fail thì nêu job/step lỗi. Không khẳng định deploy thành công chỉ vì đã push.

## Bất biến

- Docsify dùng hash route `#/...`; sidebar dùng hash link để không phụ thuộc path của GitHub Pages project site.
- `404.html` chỉ redirect deep link sang hash route; `_404.md` là nội dung not-found bên trong Docsify.
- Artifact chỉ gồm docs/public Markdown đã chọn, không upload toàn repository hoặc `.env`.
- `manual/` là tài liệu cho chủ dự án, không phải source of truth hay context mặc định của AI Agent.
