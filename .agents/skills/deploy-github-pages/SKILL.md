---
name: deploy-github-pages
description: Deploy Docsify documentation site lên GitHub Pages khi người dùng yêu cầu rõ preview/deploy/publish docs. Dùng cho `index.html`, sidebar curated, GitHub Actions Pages workflow hoặc kiểm tra trang đã deploy; không dùng khi chỉ sửa Markdown bình thường.
---

# Deploy Docsify lên GitHub Pages

## Nạp tối thiểu

1. Đọc `AGENTS.md`, `.docsify/README.md`, `index.html`, `_sidebar.md` và `.github/workflows/deploy-docs.yml`.
2. Chỉ đọc các Markdown được người dùng yêu cầu sửa; `manual/` không phải context mặc định.
3. Ngay trước commit/push, Agent chạy `node ./.docsify/generate-sidebar.mjs`; không sửa tay `_sidebar.md`. Script sinh navigation từ Markdown ở mọi độ sâu và bỏ dependency/output local.

## Kiểm tra trước deploy

1. Kiểm tra `git status --short` và scope thay đổi; không stage `.env`, `target/`, file local hay source không liên quan.
2. Kiểm tra sidebar đã sinh, `git diff --check`, tồn tại `index.html`, `_sidebar.md`, `.docsify/generate-sidebar.mjs` và `.nojekyll`.
3. Khi người dùng yêu cầu local preview, chạy Docsify từ repository root; không build Java/Docker chỉ để xem docs.

## Commit và publish

1. Chỉ commit/push khi người dùng yêu cầu rõ; stage chính xác file tài liệu/config được duyệt, không dùng `git add .`.
2. Push `main`; workflow tự deploy khi thay đổi thuộc public docs scope.
3. Nếu người dùng yêu cầu kích hoạt/kiểm tra ngay và `gh` có đăng nhập:

```powershell
$ghCli = (Get-Command gh -ErrorAction SilentlyContinue).Source
if (-not $ghCli) {
  $ghCli = @('C:\Program Files\GitHub CLI\gh.exe', 'C:\Program Files (x86)\GitHub CLI\gh.exe') |
    Where-Object { Test-Path -LiteralPath $_ } |
    Select-Object -First 1
}
& $ghCli auth status
& $ghCli workflow run deploy-docs.yml --ref main
& $ghCli run list --workflow deploy-docs.yml --limit 1
```

4. Nếu không tìm thấy `$ghCli`, báo cần mở terminal mới sau khi cài GitHub CLI hoặc thêm CLI vào PATH; không tự cài tool.
5. Khi Pages chưa từng được cấu hình, báo người dùng đặt **Settings → Pages → Source: GitHub Actions** một lần; sau đó mới trigger workflow. Không tự thay đổi repository settings nếu chưa được người dùng cho phép.
6. Báo URL Pages, run ID/kết quả và nếu fail thì nêu job/step lỗi. Không khẳng định deploy thành công chỉ vì đã push.

## Bất biến

- Docsify dùng hash route `#/...`; sidebar dùng hash link để không phụ thuộc path của GitHub Pages project site.
- Artifact mirror repository ở mọi độ sâu; loại `.git`, `.env*`, `.idea`, `target`, `node_modules`, `_site`, `logs`, `tmp` và `*.iml`.
- `manual/` là tài liệu cho chủ dự án, không phải source of truth hay context mặc định của AI Agent.
