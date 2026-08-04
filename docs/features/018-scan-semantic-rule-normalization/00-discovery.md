# 018 Scan semantic rule normalization — Discovery

Status: DISCUSSION — chưa tạo Design/Plan, chưa code.

Rule source of truth: [semantic-rules.md](./semantic-rules.md). File này chỉ giữ evidence V1; không dùng nó để quyết định rule cuối.

## Mục tiêu buổi chốt rule

Chốt một parser semantic deterministic cho evidence review của `scan-service`. Đây không phải canonical metadata và không được truy cập `catalog-service`.

## Sự thật đã kiểm chứng từ BE V1

- Folder chỉ chọn region/loại file; semantic được parse từ filename. Xem [default_path_dev.json](D:/Study/Project/file_mngt/src/main/resources/config/default_path/default_path_dev.json).
- JOKE parse `Best of <actress> [part?]` hoặc `<actress> - [<code>] [part?]`; studio lấy prefix của code qua registry. Xem [JokeFileNameParser.java](D:/Study/Project/file_mngt/src/main/java/lazy/internal/file_mngt/core/resolver/filename/JokeFileNameParser.java).
- USE ưu tiên known studio code xuất hiện trong filename, sau đó fallback `<actress> - <title> - <studioCode>`; fallback V1 có thể gán actress khi không đủ chứng cứ. Xem [UseFileNameParser.java](D:/Study/Project/file_mngt/src/main/java/lazy/internal/file_mngt/core/resolver/filename/UseFileNameParser.java).
- Registry V1 có 43 studio JOKE/118 code và 43 studio USE/56 code; phát hiện code trùng `FSDSS`, `MIST`, `Xvideos`. V2 không được chọn giá trị nạp sau. Xem [studios.json](D:/Study/Project/file_mngt/src/main/resources/json/studios.json).
- `UseWithSuffixNumberParser` không có caller, không là nguồn rule V2.

## Folder cluster → profile

| Cluster | Profile | Quyền suy luận |
| --- | --- | --- |
| Root video JOKE | `JOKE_VIDEO` | Parse filename JOKE |
| Cover/Pics, Cover/New, Cover/Orin, Gif/Joke | `JOKE_ASSET` | Parse code/tag nếu encoded trong filename |
| Syncdroid | `USE_VIDEO` | Parse filename USE |
| FullPics, FullPicsNew, Gif/Use, Honeyview, Landscape, Portrait | `USE_ASSET` | Parse filename USE nếu khớp rule strict |
| Album, Manhwa | `USE_ALBUM` | Identity folder; chỉ parse semantic khi leaf folder khớp rule strict |

Folder không được dùng như `studioName` hay `actressNames`.
