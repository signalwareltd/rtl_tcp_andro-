---
title: Architecture diagram
audience: agent
scope: Public module map
status: complete
snapshot:
  app: 3e261f5a7576
---

# Architecture diagram

```mermaid
flowchart TD
  PRODUCT[Product]
  M0[app]
  PRODUCT -->|contains| M0
  M1[hackrf]
  PRODUCT -->|contains| M1
  M2[rtlsdr]
  PRODUCT -->|contains| M2
  M3[sdrdrivertools]
  PRODUCT -->|contains| M3
```
