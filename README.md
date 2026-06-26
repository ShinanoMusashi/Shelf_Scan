# ShelfScape

Photograph a bookshelf, let a local vision model read the spines, and turn the
result into an interactive, editable, saved library.

ShelfScape sends a shelf photo to a locally-run vision-language model
(**MiniCPM-V** via [Ollama](https://ollama.com)), draws each detected book as a
clickable box, and lets you record details (author, ISBN, year, notes) that
persist between runs. A "bookshelf" view redraws the books as tidy ordered spines.

> The book recognition (MiniCPM-V) and the model runner (Ollama) are third-party
> and not my work — see [`NOTICE.md`](NOTICE.md). Everything under
> `src/shelfscape/` is mine.

See [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) for diagrams of how the app is
put together (and the future phone/server target).

## Build & run

Plain Java, no external libraries. Java 8+.

```bash
javac -d bin $(find src -name '*.java')
java -cp bin shelfscape.app.MainApp
```

Scanning needs Ollama running locally with the model pulled:

```bash
ollama serve            # in one terminal
ollama pull minicpm-v   # one time
```

## Structure

```
src/shelfscape/
├── app/         MainApp            — entry point
├── view/        MainFrame, ShelfPanel, BookDetailPanel, ScanDialog, ArchiveBrowser
├── controller/  ScanController     — orchestrates a scan on a worker thread
├── service/     VlmRecognizer + MiniCpmVRecognizer  (MiniCPM-V via Ollama)
│                ArchiveStore       — save/load the archive as plain text
├── model/       Book, Shelf, Archive, Detection, ReadingStatus
└── util/        Json, Exif, ImageRotation, TextLine
```

View / Controller / Service / Model layering keeps the recognition, state, and
UI separate so pieces can be swapped or reused.

## Roadmap

- Smarter scanning: auto-split a large photo into the fewest tiles needed for
  the model to read fine print, and detect shelf rows automatically.
- Archive browser: list, open, rename, and delete saved shelves.
- Longer term: a mobile (Android) front-end over the same core logic.
