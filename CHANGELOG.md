# ShelfScape — Dev Log

A running log of what's been built. Newest first.

## 2026-06-24 — Smart scan: rows + adaptive tiling  *(in progress)*

Goal: stop relying on the manual "Scan Region", and read a whole shelf (even
multiple rows, even small print) automatically.

- New UI-free package `shelfscape.scan` (pure geometry, reusable on any platform):
  - `RowDetector` — clusters detections into shelf **rows** by vertical overlap.
  - `TilePlanner` — decides the **fewest tiles** a row needs (by book density and
    by resolution) and produces overlapping tile rectangles.
  - `DetectionMerge` — drops duplicate books seen on overlapping tile seams.
- `ScanController` rewritten: **first pass** reads the whole photo to find rows →
  each row is re-scanned in just enough tiles → results merged, ordered, tidied.
  Rows that already read fine cost no extra scan.
- `Book` gained a `row` field (saved at the end of the line; old saves still load).
- `ShelfPanel` bookshelf view now draws **multiple stacked rows**, each on its own
  board; click-to-select works across rows.
- Verified headlessly: row clustering, tile counts, tile overlap, dedupe.
  **Not yet tested against the live model end-to-end** (pending a real multi-row
  photo scan).

## 2026-06-24 — Refocus as a real, expandable project

- Moved into its own Git repo and pushed to
  `github.com/ShinanoMusashi/Shelf_Scan` (force-pushed clean core).
- Added a **Gradle** build (wrapper 8.14, `application` plugin, Java 17 target)
  and moved sources to the standard `src/main/java/` layout — also what a future
  Android module expects.
- Trimmed to the core product: removed grading artifacts (TEST_MATRIX, design
  report), IDE files, the demo fallback + sample image, and the run/setup scripts.
- New direction: build toward a real app; longer-term goal is a mobile front-end
  over the same core logic.

## Earlier (school-project phase)

- Java Swing MVC app: photograph a shelf → local **MiniCPM-V** (via Ollama) reads
  the spines → interactive, editable, saved library. No external Java libraries.
- Core pieces built along the way:
  - Ollama HTTP client + hand-written JSON (`util.Json`); handles the model's
    inconsistent pixel-vs-normalised box coordinates.
  - EXIF auto-rotate on load (`util.Exif`); spine-orientation chooser
    (`util.ImageRotation`) so the model reads titles upright, boxes mapped back.
  - `ShelfPanel`: photo + clickable boxes (SCAN view) and a tidy ordered
    bookshelf (SHELF view); drag-to-select + "Scan Region".
  - Editable `BookDetailPanel` (author/ISBN/year/status/notes).
  - Plain-text persistence (`ArchiveStore` + `Book.toString`/`fromString`);
    auto-saves and restores the most recent shelf on startup.
- Dropped along the way: an Open Library metadata lookup (removed) and a Python
  OCR prototype (deleted) — see git history / `NOTICE.md`.

---

### Backlog / ideas

- Verify smart scan against the live model; tune tile thresholds on real photos.
- Adaptive *re-tile*: if a tile finds far more books than predicted, split further.
- Archive browser: list / open / rename / delete saved shelves.
- Mobile (Android) front-end over the `model` + `scan` core.
