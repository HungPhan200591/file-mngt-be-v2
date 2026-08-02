---
name: deploy-github-pages
description: Quy trình tự động quét tài liệu, sinh cây sidebar _sidebar.md, commit và kích hoạt deploy trang web Docsify lên GitHub Pages. Dùng khi người dùng yêu cầu deploy docs, cập nhật tài liệu public hoặc xuất bản trang Docsify lên GitHub Pages.
---

# Deploy GitHub Pages Workflow for Docsify

Skill này hướng dẫn AI Agent quy trình từng bước để kiểm tra, cập nhật cây tài liệu Docsify và đẩy bản công khai lên GitHub Pages khi người dùng yêu cầu.

## Các bước thực hiện

### 1. Quét lại toàn bộ cây tài liệu & Sinh `_sidebar.md`
Chạy script Node.js tại root dự án để đảm bảo toàn bộ file `.md` mới tạo đều được cập nhật vào `_sidebar.md`:
```bash
node generate-sidebar.js
```

### 2. Kiểm tra trạng thái Git
Kiểm tra các thay đổi tài liệu bằng:
```bash
git status
```

### 3. Stage & Commit thay đổi
Thực hiện stage toàn bộ tài liệu và file cấu hình vừa cập nhật:
```bash
git add .
git commit -m "docs: update documentation and sidebar for GitHub Pages deployment"
```

### 4. Push code lên GitHub
Đẩy commit lên nhánh chính (`main` hoặc `master` tùy theo nhánh hiện tại):
```bash
git push origin main
```

### 5. Kích hoạt GitHub Action Workflow (Nếu có cài GitHub CLI)
Nếu máy người dùng có sẵn GitHub CLI (`gh`), kích hoạt trực tiếp workflow:
```bash
gh workflow run deploy-docs.yml
```
Nếu máy chưa cài `gh`, nhắc người dùng vào tab **Actions** trên GitHub Web $\rightarrow$ chọn **Deploy Docsify to GitHub Pages** $\rightarrow$ bấm nút **Run workflow**.

## Nguyên tắc an toàn
- Không commit file rác, file `.env` hay secret.
- Đảm bảo `generate-sidebar.js` và `index.html` tại root hoạt động bình thường trước khi push.
- Thông báo rõ ràng mã hash commit và link GitHub Pages sau khi hoàn thành.
