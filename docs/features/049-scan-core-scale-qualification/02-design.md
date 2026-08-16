# FT-049 — Design: Scan-Core Scale Qualification

Status: `READY`  
Owner: `scan-service`

## 1. Qualification flow

```mermaid
flowchart TB
    ENV["Hardware envelope"] --> MATRIX["Workload matrix"]
    MATRIX --> RUN["Repeated benchmark runs"]
    RUN --> METRIC["Phase and resource metrics"]
    METRIC --> CHECK{"Correctness passes?"}
    CHECK -->|"Yes"| REPORT["Scan-core qualification"]
    CHECK -->|"No"| BLOCK["Reject result"]
    REPORT --> SLO(["Qualified scope"])
    BLOCK --> SLO

    style ENV fill:#455A64,stroke:#fff,stroke-width:2px,color:#fff
    style MATRIX fill:#2196F3,stroke:#fff,stroke-width:2px,color:#fff
    style RUN fill:#FF9800,stroke:#fff,stroke-width:2px,color:#fff
    style METRIC fill:#009688,stroke:#fff,stroke-width:2px,color:#fff
    style CHECK fill:#2196F3,stroke:#fff,stroke-width:2px,color:#fff
    style REPORT fill:#4CAF50,stroke:#fff,stroke-width:2px,color:#fff
    style BLOCK fill:#E91E63,stroke:#fff,stroke-width:2px,color:#fff
    style SLO fill:#4CAF50,stroke:#fff,stroke-width:2px,color:#fff
```

## 2. Evidence boundary

The benchmark includes production Scan Service orchestration, synthetic cursor, parsing, reconciliation and PostgreSQL persistence. Filesystem and Catalog I/O remain excluded by design. The report must state this boundary beside every headline number.

The result is a capacity observation for one environment, not a universal production SLO. Cross-service approval readiness requires a separate end-to-end benchmark with operation watermark and downstream backlog evidence.

## 3. Decision rules

- Correctness failure invalidates the performance result.
- A single fast run is not a qualification.
- Cold and warm results are separate populations.
- Improvements must be compared against the immediately preceding FT implementation.
- A regression in warm reconciliation cannot be hidden by a faster cold run.
