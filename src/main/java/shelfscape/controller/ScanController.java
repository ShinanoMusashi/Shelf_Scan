// Author: Ruiqi Huang
// Controller: runs a scan and tidies the boxes. Depends only on the VlmRecognizer interface, so the recogniser can
// be swapped. Titles come from the model (for now the app does not separate author from book names, this will be
// implemented using a LLM model in future if I have time) the user adds the rest.
//
// Smart scan: one quick first pass finds the shelf ROWS, then each row is re-scanned
// in just enough horizontal tiles for the model to read small spine text. Tiles are
// cropped from the photo, so each is sent at higher effective resolution than the
// whole photo would be.
package shelfscape.controller;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import shelfscape.model.Book;
import shelfscape.model.Detection;
import shelfscape.model.Shelf;
import shelfscape.scan.DetectionMerge;
import shelfscape.scan.RowDetector;
import shelfscape.scan.TilePlanner;
import shelfscape.service.VlmRecognizer;
import shelfscape.util.ImageRotation;

public class ScanController {

    // Start the interface
    public interface ProgressListener {
        void update(int percent, String message);
    }

    private final VlmRecognizer recognizer;

    // Replace the model's overlapping/imprecise boxes with clean non-overlapping
    // columns from each book's x-position. Set false to draw the raw model boxes
    // instead. (I keep it true because the OCR positions are not exact.)
    private boolean singleRowLayout = true;

    public ScanController(VlmRecognizer recognizer) {
        this.recognizer = recognizer;
    }

    // Run a full scan and build a Shelf. Heavy/blocking (model inference) — must
    // be called off the Swing thread. rotationCw rotates the photo so spines read
    // upright; listener is optional. Throws if recognition fails (e.g. no Ollama).
    public Shelf scanShelf(BufferedImage photo, String shelfName, int rotationCw,
            ProgressListener listener) throws Exception {

        int origW = photo.getWidth();
        int origH = photo.getHeight();

        // FIRST PASS: read the whole photo once to find the rows + rough density.
        report(listener, -1, "First pass: reading the whole photo to find the shelf rows :)");
        long t0 = System.currentTimeMillis();
        List<Detection> first = scanTile(photo, new Rectangle(0, 0, origW, origH), rotationCw);
        report(listener, 20, String.format("First pass read %d book(s) in %.1fs. Finding rows…",
                first.size(), (System.currentTimeMillis() - t0) / 1000.0));

        // Group the detections into shelf rows.
        List<List<Detection>> rows = RowDetector.detectRows(first);
        if (rows.isEmpty()) {
            rows = new ArrayList<>();
            rows.add(first); // nothing had a box — keep whatever we got as one row
        }

        // Decide how many detail tiles each row needs (rows that read fine reuse
        // the first pass, so they cost no extra scan).
        int[] tilesPerRow = new int[rows.size()];
        int plannedTiles = 0;
        for (int r = 0; r < rows.size(); r++) {
            Rectangle band = bandOf(rows.get(r), origW, origH);
            tilesPerRow[r] = TilePlanner.tilesForRow(rows.get(r).size(), band.width);
            if (tilesPerRow[r] > 1) {
                plannedTiles += tilesPerRow[r];
            }
        }
        report(listener, 30, String.format("Found %d row(s); scanning %d detail tile(s)…",
                rows.size(), plannedTiles));

        Shelf shelf = new Shelf(generateId(), shelfName);
        shelf.setScanDate(LocalDate.now().toString());

        int doneTiles = 0;
        for (int r = 0; r < rows.size(); r++) {
            List<Detection> rowDets;
            if (tilesPerRow[r] <= 1) {
                rowDets = rows.get(r); // first pass already read this row well enough
            } else {
                Rectangle band = bandOf(rows.get(r), origW, origH);
                List<Rectangle> tiles = TilePlanner.horizontalTiles(band, tilesPerRow[r]);
                rowDets = new ArrayList<>();
                for (int t = 0; t < tiles.size(); t++) {
                    rowDets.addAll(scanTile(photo, tiles.get(t), rotationCw));
                    doneTiles++;
                    int percent = 30 + (int) (65.0 * doneTiles / Math.max(1, plannedTiles));
                    report(listener, percent, String.format("Row %d/%d: scanned tile %d/%d",
                            r + 1, rows.size(), t + 1, tiles.size()));
                }
                // Overlapping tiles double-detect books on the seams — drop those.
                rowDets = DetectionMerge.dedupe(rowDets);
            }

            // Order left-to-right and tidy into clean, non-overlapping columns.
            rowDets.sort(Comparator.comparingInt(ScanController::centreX));
            if (singleRowLayout) {
                tidyRowColumns(rowDets, origW);
            }
            for (Detection d : rowDets) {
                Book b = new Book(d.getRawTitle(), d.getBbox());
                b.setRow(r);
                shelf.addBook(b);
            }
        }

        report(listener, 100, String.format("Done — %d book(s) across %d row(s).",
                shelf.getBooks().size(), rows.size()));
        return shelf;
    }

    // Crop a rectangle of the photo, rotate it upright for the model, detect, then
    // map every box back to the ORIGINAL photo's coordinates.
    private List<Detection> scanTile(BufferedImage photo, Rectangle rect, int rotationCw)
            throws Exception {
        BufferedImage crop = photo.getSubimage(rect.x, rect.y, rect.width, rect.height);
        BufferedImage forVlm = ImageRotation.rotate(crop, rotationCw);
        List<Detection> dets = recognizer.detect(forVlm);
        for (Detection d : dets) {
            Rectangle box = d.getBbox();
            if (box == null) {
                continue;
            }
            // Undo the rotation (back to upright-crop pixels), then offset to the
            // tile's place in the full photo.
            if (rotationCw != 0) {
                box = ImageRotation.unrotateRect(box, rotationCw, crop.getWidth(), crop.getHeight());
            }
            d.setBbox(new Rectangle(box.x + rect.x, box.y + rect.y, box.width, box.height));
        }
        return dets;
    }

    // The full-width horizontal strip a row occupies, padded a little, clamped.
    private static Rectangle bandOf(List<Detection> row, int origW, int origH) {
        int top = Integer.MAX_VALUE;
        int bottom = Integer.MIN_VALUE;
        for (Detection d : row) {
            Rectangle b = d.getBbox();
            if (b == null) {
                continue;
            }
            top = Math.min(top, b.y);
            bottom = Math.max(bottom, b.y + b.height);
        }
        if (top == Integer.MAX_VALUE) {
            return new Rectangle(0, 0, origW, origH); // no boxes — use the whole photo
        }
        int pad = (int) Math.round((bottom - top) * 0.08);
        top = clampInt(top - pad, 0, origH);
        bottom = clampInt(bottom + pad, 0, origH);
        return new Rectangle(0, top, origW, Math.max(1, bottom - top));
    }

    // The position of a box; a missing box gets a very big x so it sorts last.
    private static int centreX(Detection d) {
        Rectangle b = d.getBbox();
        return b == null ? Integer.MAX_VALUE : b.x + b.width / 2;
    }

    // Replace a row's overlapping/imprecise boxes with clean, non-overlapping
    // columns: one shared height (the row's median) and x split at the midpoints
    // between neighbouring book centres.
    private static void tidyRowColumns(List<Detection> rowDets, int origW) {
        List<Detection> boxed = new ArrayList<>();
        for (Detection d : rowDets) {
            if (d.getBbox() != null) {
                boxed.add(d);
            }
        }
        int n = boxed.size();
        if (n == 0) {
            return;
        }
        boxed.sort(Comparator.comparingInt(ScanController::centreX));

        int[] cx = new int[n];
        int[] tops = new int[n];
        int[] bottoms = new int[n];
        for (int i = 0; i < n; i++) {
            Rectangle b = boxed.get(i).getBbox();
            cx[i] = b.x + b.width / 2;
            tops[i] = b.y;
            bottoms[i] = b.y + b.height;
        }
        int top = median(tops);
        int bottom = median(bottoms);
        if (bottom <= top) {
            bottom = top + 1;
        }
        int height = bottom - top;

        for (int i = 0; i < n; i++) {
            int leftGap = (i > 0) ? (cx[i] - cx[i - 1]) / 2
                    : (n > 1 ? (cx[1] - cx[0]) / 2 : origW / 4);
            int rightGap = (i < n - 1) ? (cx[i + 1] - cx[i]) / 2
                    : (n > 1 ? (cx[n - 1] - cx[n - 2]) / 2 : origW / 4);
            int left = clampInt(cx[i] - leftGap, 0, origW);
            int right = clampInt(cx[i] + rightGap, 0, origW);
            if (right <= left) {
                right = Math.min(origW, left + 1);
            }
            boxed.get(i).setBbox(new Rectangle(left, top, right - left, height));
        }
    }

    // Middle value
    private static int median(int[] values) {
        int[] copy = Arrays.copyOf(values, values.length);
        Arrays.sort(copy);
        return copy[copy.length / 2];
    }

    // Keep v within [lo, hi] (which is basically low and high).
    private static int clampInt(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    // In case if one of the value is actually called
    private static void report(ProgressListener listener, int percent, String message) {
        if (listener != null) {
            listener.update(percent, message);
        }
    }

    // I don't think it's possible to have two shelves created at the same time so just use that as the id :)
    private static String generateId() {
        return "shelf-" + System.currentTimeMillis();
    }
}
