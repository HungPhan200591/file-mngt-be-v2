# 1. Business model

## Từ filesystem đến media có cấu trúc

Filesystem chỉ biết file và folder. Nó không tự biết các file dưới đây cùng thuộc một video:

```text
Root/Actress - [START-001].mp4
CoverPics/Actress - [START-001] (1).jpg
CoverPics/Actress - [START-001] (2).jpg
GifJoke/Actress - [START-001] (1).gif
```

Backend V2 chuyển chúng thành một `Subject` và nhiều `Asset`.

```mermaid
flowchart TB
    FILES["<font color='white'>Filesystem<br/>MP4 + JPG + GIF</font>"] --> PARSE["<font color='white'>Scan<br/>extract identity</font>"]
    PARSE --> SUBJECT["<font color='white'>Subject VIDEO<br/>JOKE / START-001</font>"]
    SUBJECT --> VIDEO["<font color='white'>Asset<br/>PRIMARY_VIDEO</font>"]
    SUBJECT --> IMAGE["<font color='white'>Assets<br/>IMAGE</font>"]
    SUBJECT --> GIF["<font color='white'>Assets<br/>GIF</font>"]

    style FILES fill:#009688,stroke:#fff,stroke-width:2px
    style PARSE fill:#FF9800,stroke:#fff,stroke-width:2px
    style SUBJECT fill:#2196F3,stroke:#fff,stroke-width:2px
    style VIDEO fill:#4CAF50,stroke:#fff,stroke-width:2px
    style IMAGE fill:#4CAF50,stroke:#fff,stroke-width:2px
    style GIF fill:#4CAF50,stroke:#fff,stroke-width:2px
```

## Subject là gì?

`Subject` là đối tượng media logic được quản lý và hiển thị.

Hiện có hai loại:

| `subjectType` | Ý nghĩa |
| --- | --- |
| `VIDEO` | Một video chính cùng ảnh/GIF liên quan |
| `ALBUM` | Một album độc lập, có thể có hoặc không liên kết video |

Identity của Subject là bộ ba:

```text
(region, subjectType, identityKey)
```

Ví dụ `JOKE + VIDEO + START-001` khác `USE + VIDEO + START-001`, dù chuỗi key giống nhau.

## Asset là gì?

`Asset` là một file vật lý thuộc Subject.

| `role` | Ý nghĩa |
| --- | --- |
| `PRIMARY_VIDEO` | Video chính của Subject VIDEO; tối đa một file |
| `VIDEO` | Video bổ sung, chủ yếu dùng cho Album |
| `IMAGE` | Ảnh thuộc video/album |
| `GIF` | GIF thuộc video/album |

Asset không lưu absolute path. Locator canonical gồm:

```text
storageKey + relativePath
```

- `storageKey`: tên logic của root đã cấu hình, ví dụ `fixture-joke-video`.
- `relativePath`: đường dẫn tương đối trong root.
- Absolute path chỉ tồn tại trong cấu hình local của Scan/Worker, không đi qua API/Kafka.

## JOKE được nhận diện như thế nào?

JOKE dùng code trong dấu `[]` làm `identityKey`.

| File | Kết quả |
| --- | --- |
| `Actress - [START-001].mp4` | Subject `JOKE/VIDEO/START-001`, asset `PRIMARY_VIDEO` |
| `Actress - [START-001] (1).jpg` | Cùng Subject, asset `IMAGE` |
| `Actress - [START-001] (1).gif` | Cùng Subject, asset `GIF` |

Video, ảnh và GIF có thể được scan ở các root khác nhau. Chúng hội tụ vì có cùng identity Subject.

## USE được nhận diện như thế nào?

USE không có code ổn định như JOKE nên dùng basename chuẩn hóa.

Ví dụ:

```text
Syncdroid/Hibiki Natsume - A Title - Studio.mp4
FullPics/Hibiki Natsume - A Title - Studio (1).jpg
GifUse/Hibiki Natsume - A Title - Studio (1).gif
```

Scan bỏ extension, bỏ suffix số `(1)`, trim/thu gọn khoảng trắng và lowercase để tạo cùng `identityKey`.

Điều này giải quyết khác biệt trình bày nhỏ, nhưng chưa phải metadata Actress/Title/Studio đã được tách thành entity riêng.

## USE Album là case đặc biệt

Folder dưới root Album tạo Subject loại `ALBUM`. Folder là identity chính, không tự gộp vào video Syncdroid.

```mermaid
flowchart TB
    FOLDER["<font color='white'>USE Album folder</font>"] --> ALBUM["<font color='white'>Subject ALBUM<br/>identity theo folder</font>"]
    ALBUM --> AIMG["<font color='white'>IMAGE assets</font>"]
    ALBUM --> AVID["<font color='white'>VIDEO assets<br/>nếu có</font>"]
    ALBUM -.->|FULL_ALBUM_OF<br/>optional, review| SYNC["<font color='white'>Subject VIDEO<br/>Syncdroid</font>"]

    style FOLDER fill:#009688,stroke:#fff,stroke-width:2px
    style ALBUM fill:#2196F3,stroke:#fff,stroke-width:2px
    style AIMG fill:#4CAF50,stroke:#fff,stroke-width:2px
    style AVID fill:#4CAF50,stroke:#fff,stroke-width:2px
    style SYNC fill:#2196F3,stroke:#fff,stroke-width:2px
```

Một Album có thể là full album của một video Syncdroid, nhưng quan hệ đó là optional và cần review khi không chắc chắn. Quan hệ `FULL_ALBUM_OF` hiện mới là concept mục tiêu, chưa có table Catalog thực tế.

## Proposal và Issue

Scan không tự ghi thẳng Catalog vì parser có thể sai.

- `Proposal`: Scan hiểu được file và đề xuất Subject/Asset candidate.
- `Issue`: file không khớp profile hoặc không thể phân tích an toàn.
- `APPROVE`: chấp nhận proposal và phát event sang Catalog.
- `REJECT`: lưu quyết định từ chối, không phát event.

Đây là ranh giới quan trọng: Scan đưa ra đề xuất; Catalog mới là nơi dữ liệu trở thành canonical.

## Business đã có và chưa có

Đã có trong code/database:

- Region `JOKE`, `USE`.
- Subject `VIDEO`, `ALBUM`.
- Asset và locator.
- Scan proposal/issue/decision.

Chưa được triển khai canonical đầy đủ:

- Actress, Studio, Tag và alias.
- Quan hệ Album ↔ Video.
- Title/code/studio dưới dạng field/entity chuẩn hóa.
- Luật merge/split/relink Subject nâng cao.

FT013 không bổ sung các business entity này; nó chỉ bổ sung metadata kỹ thuật cho Asset hiện có.
