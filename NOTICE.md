# Third-Party Components — Attribution

**This is important** This file makes clear which parts of ShelfScape are NOT MY WORK

## What I did NOT write

| Component | What it is | Who made it | How ShelfScape uses it                                                                                          |
| --- | --- | --- |-----------------------------------------------------------------------------------------------------------------|
| **MiniCPM-V** | A vision-language model that does the actual book recognition from the photo. | OpenBMB (open-source model). | My app sends the photo to it over HTTP and reads back the result. I did NOT train, build, or modify this model. |
| **Ollama** | A separate program that runs the MiniCPM-V model locally and exposes it at `http://localhost:11434`. | ollama.com. | I run it as an external service; it is NOT part of my code and must be installed separately.                    |

## What IS my own work

Everything under **`src/shelfscape/`** is written by me (Ruiqi Huang):

- The whole Java Swing application (View / Controller / Service / Model layers).
- The **integration code** that connects to the external model:
  - `MiniCpmVRecognizer` — encodes the image, builds the request, sends it to
    Ollama, and parses the response. (The recognition itself is done by the
    external model, not by this class.)

