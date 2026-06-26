# ShelfScape — Architecture

## Today: one Java desktop app + a local AI model

Everything runs on your computer. ShelfScape is a single Java (Swing) program;
the only external piece is the **MiniCPM-V** vision model, served by **Ollama**
(a separate program — not my work, see [`NOTICE.md`](../NOTICE.md)).

```mermaid
flowchart TB
  USER["🧑 User<br/>picks a bookshelf photo"]

  subgraph APP["🖥️ ShelfScape — one Java desktop app (your computer)"]
    direction TB

    subgraph VIEW["🪟 View — Swing UI (shelfscape.view)"]
      MAIN["MainFrame<br/>menu · toolbar · file chooser · worker thread"]
      SDLG["ScanDialog<br/>progress + log"]
      SPANEL["ShelfPanel<br/>SCAN view: boxes on photo<br/>SHELF view: stacked rows"]
      DPANEL["BookDetailPanel<br/>edit title/author/ISBN/notes"]
    end

    subgraph CTRL["⚙️ Controller (shelfscape.controller)"]
      SCAN["ScanController.scanShelf()<br/>orchestrates the scan<br/>(background thread)"]
    end

    subgraph LOGIC["🧩 Scan logic — pure, no UI (shelfscape.scan)"]
      ROWS["RowDetector<br/>group detections into rows"]
      TILES["TilePlanner<br/>fewest tiles per row"]
      MERGE["DetectionMerge<br/>drop seam duplicates"]
    end

    subgraph SVC["🔌 Service (shelfscape.service)"]
      REC["MiniCpmVRecognizer<br/>crop · encode · HTTP POST · parse JSON"]
      STORE["ArchiveStore<br/>save/load plain-text archive"]
    end

    subgraph UTIL["🛠️ Util (shelfscape.util)"]
      EXIF["Exif · ImageRotation<br/>orient photo + rotate tiles"]
      JSON["Json · TextLine<br/>hand-written JSON + escaping"]
    end

    subgraph MODEL["📚 Model (shelfscape.model)"]
      DATA["Book · Shelf · Archive<br/>Detection · ReadingStatus"]
    end
  end

  subgraph EXT["🌐 External — NOT my work (see NOTICE.md)"]
    OLLAMA["Ollama server<br/>localhost:11434"]
    VLM["MiniCPM-V<br/>vision model — reads spine titles"]
  end

  FILE["💾 shelfscape_archive.txt<br/>on disk"]

  USER -->|"choose photo"| MAIN
  MAIN -->|"start scan"| SCAN
  SCAN -->|"progress + log"| SDLG
  SCAN -->|"1 · first pass (whole photo)"| REC
  SCAN -.->|"uses"| EXIF
  SCAN -->|"2 · find rows"| ROWS
  SCAN -->|"3 · plan tiles"| TILES
  SCAN -->|"4 · re-scan each tile"| REC
  SCAN -->|"5 · merge duplicates"| MERGE
  REC -->|"POST image"| OLLAMA
  OLLAMA --> VLM
  VLM -->|"titles + boxes (JSON)"| REC
  REC -.->|"uses"| JSON
  SCAN -->|"builds"| DATA
  SCAN -->|"render"| SPANEL
  SPANEL -->|"click a book"| DPANEL
  DPANEL -->|"edit"| DATA
  SCAN -->|"save"| STORE
  DPANEL -->|"save"| STORE
  STORE <-->|"read / write"| FILE
  MAIN -->|"load on startup"| STORE

  classDef user fill:#fff4d6,stroke:#caa53a;
  classDef view fill:#e8f0fe,stroke:#4a78d0;
  classDef ctrl fill:#ede4fb,stroke:#7e57c2;
  classDef logic fill:#eaf7ea,stroke:#4caf50;
  classDef svc fill:#fde8e8,stroke:#d04a4a;
  classDef util fill:#f0f0f0,stroke:#888;
  classDef model fill:#e0f7fa,stroke:#0097a7;
  classDef ext fill:#fce4ec,stroke:#c2185b;
  classDef disk fill:#ffffff,stroke:#555,stroke-dasharray:4 3;
  class USER user;
  class MAIN,SDLG,SPANEL,DPANEL view;
  class SCAN ctrl;
  class ROWS,TILES,MERGE logic;
  class REC,STORE svc;
  class EXIF,JSON util;
  class DATA model;
  class OLLAMA,VLM ext;
  class FILE disk;
```

### How one scan flows (the numbered arrows)

1. **First pass** — the whole photo is sent to the model once, to find roughly
   where the books are.
2. **Find rows** — `RowDetector` groups those detections into shelf rows by
   vertical position.
3. **Plan tiles** — `TilePlanner` decides the *fewest* horizontal tiles each row
   needs so small spine text is big enough for the model to read.
4. **Re-scan tiles** — each tile is cropped, rotated upright, sent to the model;
   its boxes are mapped back onto the original photo.
5. **Merge** — `DetectionMerge` removes books that were seen twice on overlapping
   tile seams. The result becomes a `Shelf` of `Book`s, rendered and saved.

> Note: steps 2, 3, 5 are **pure logic** with no UI or image classes that bind
> them to the desktop — that's deliberate, so they can be reused on a server (see
> below).

---

## Future target: phone front-end + server

The long-term goal is a phone app. Swing can't run on a phone, but the green
`shelfscape.scan` core (and the model/recognizer idea) carries straight over —
only the UI and the image plumbing get swapped.

```mermaid
flowchart TB
  subgraph PHONE["📱 Phone / browser (future)"]
    PUI["App or web page<br/>take photo · browse shelves · edit"]
  end

  subgraph SERVER["🖥️ Server (home computer)"]
    subgraph CPU["⚙️ CPU"]
      WEB["REST API<br/>runs the scan pipeline"]
      CORE["Reused core<br/>RowDetector · TilePlanner · DetectionMerge"]
      DB["Archive store<br/>(database or files)"]
    end
    subgraph GPU["🚀 GPU"]
      M["MiniCPM-V (or larger VLM)<br/>via Ollama / llama.cpp"]
    end
  end

  PUI -->|"1 · upload photo + topic (HTTP)"| WEB
  WEB -.->|"uses"| CORE
  WEB -->|"2 · read these titles?"| M
  M -->|"3 · titles + boxes"| WEB
  WEB <-->|"store / fetch"| DB
  WEB -->|"4 · shelf result (JSON)"| PUI

  classDef phone fill:#e8f0fe,stroke:#4a78d0;
  classDef cpu fill:#eaf7ea,stroke:#4caf50;
  classDef gpu fill:#fde8e8,stroke:#d04a4a;
  class PUI phone;
  class WEB,CORE,DB cpu;
  class M gpu;
```

The bridge from "today" to "future" is to pull the scan orchestration out of
`MainFrame` into a headless service that a small HTTP server can call — the
desktop UI and a phone UI then both talk to the same core.
