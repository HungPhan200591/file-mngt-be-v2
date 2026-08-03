# 017 Scan semantic metadata extraction — Design

Owner: `scan-service` / `scan_db`  
Brief: [01-brief.md](./01-brief.md)

## High Level Design

```mermaid
flowchart TB
    FILE["<font color='white'>Relative path<br/>and filename</font>"] -->|"Extract evidence"| EXTRACTOR["<font color='white'>Scan metadata<br/>extractor</font>"]
    EXTRACTOR -->|"Candidate and evidence"| PROPOSAL["<font color='white'>Scan proposal</font>"]
    PROPOSAL -->|"Persist JSON"| DB[("<font color='white'>scan_db</font>")]
    DB -->|"Review response"| API["<font color='white'>Scan proposal API</font>"]

    style FILE fill:#009688,stroke:#fff,stroke-width:2px
    style EXTRACTOR fill:#FF9800,stroke:#fff,stroke-width:2px
    style PROPOSAL fill:#4CAF50,stroke:#fff,stroke-width:2px
    style DB fill:#9C27B0,stroke:#fff,stroke-width:2px
    style API fill:#2196F3,stroke:#fff,stroke-width:2px
```

## Quyết định

- Evidence JSON là snapshot parse tại thời điểm scan, không tái parse khi đọc proposal.
- Common evidence: `parserVersion`, `fileName`, `fileStem`, `extension`, `parentPath`, `pathSegments`, semantic `title`, `actressNames`, `studioName`, `tagNames`, status/warnings.
- Profile evidence: `bracketCode` cho `JOKE_*`, `normalizedBasename` cho `USE_VIDEO`/`USE_ASSET`, `albumRelativePath` cho `USE_ALBUM`.
- `sourceRelativePath` là nguồn duy nhất; không log/trả absolute root path.
- Actress/studio/tag không được suy ra từ segment chung. Baseline trả list/string rỗng cùng warning `*_NOT_ENCODED`; feature sau chỉ thêm extractor có rule theo root/profile và warning khi ambiguous.

## Contract và ownership

- `scan_proposal.evidence` đã tồn tại; dùng JSON string được serialize/deserialize ở Scan, không migration.
- `ScanProposal` response đã có field OpenAPI `evidence`; implementation phải hydrate field này. Đây là additive completion, không đổi request/event.
- Không đổi `media.file.discovered.v1`: Catalog vẫn chỉ nhận metadata canonical hiện có sau approval.

## Failure và compatibility

- Không parse được evidence phụ thì proposal vẫn được tạo với common evidence; không làm fail cả run.
- Run cũ có `{}` trả object rỗng; không backfill hay mutation.
- Parser version cho phép thêm field tương thích mà không diễn giải lại snapshot cũ.
