// Author: Ruiqi Huang
// Controller: runs a scan and tidies the boxes. Depends only on the VlmRecognizer interface, so the recogniser can
// be swapped (real or demo, the demo is the fallback). Titles come from the model (for now the app does not separate
// author from book names, this will be implemeted using a LLM model in future if I have time) the user adds the rest.
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
import shelfscape.service.VlmRecognizer;
import shelfscape.util.ImageRotation;

public class ScanController {

    // Start the interface
    public interface ProgressListener {
        void update(int percent, String message);
    }

    private final VlmRecognizer recognizer;

    // Replace the model's overlapping/imprecise boxes with boxes that do not overlap
    // vertical strips from each book's x-position. Set false to draw the raw model boxes instead. (for this I always
    // make it true because for now OCR position is a really strange thing it does not output exact positions, I'll improve
    // it in the future)
    private boolean singleRowLayout = true;

    public ScanController(VlmRecognizer recognizer) {
        this.recognizer = recognizer;
    }

    // Run a full scan and build a Shelf. Heavy/blocking (model inference) — must
    // be called off the Swing thread. rotationCw rotates the photo so spines read
    // upright; listener is optional. Throws if recognition fails (e.g. no Ollama).
    public Shelf scanShelf(BufferedImage photo, String shelfName, int rotationCw,
            ProgressListener listener) throws Exception {

        // Rotate the picture!!! this is important cause OCR only read text that are in the correct orientation
        BufferedImage forVlm = ImageRotation.rotate(photo, rotationCw);
        int origW = photo.getWidth();
        int origH = photo.getHeight();

        // A response here (mainly for debugging) to show that the stuff is up and running
        report(listener, -1, "Sending photo to MiniCPM-V OCR :)");
        long t0 = System.currentTimeMillis();
        List<Detection> detections = recognizer.detect(forVlm);
        long vlmMs = System.currentTimeMillis() - t0;
        report(listener, 60, String.format("Detected %d book(s) in %.1fs",
                detections.size(), vlmMs / 1000.0));

        // Put the box back on to the image and show it
        if (rotationCw != 0) {
            for (Detection d : detections) {
                if (d.getBbox() != null) {
                    d.setBbox(ImageRotation.unrotateRect(d.getBbox(), rotationCw, origW, origH));
                }
            }
        }

        // Here we assume that the user only selected one row, so we can use the x coordinate to determine the order
        detections.sort(Comparator.comparingInt(ScanController::centreX));

        // In case the box is actually overlapping we will replace them
        if (singleRowLayout) {
            applySingleRowLayout(detections, origW, origH);
        }

        Shelf shelf = new Shelf(generateId(), shelfName);
        shelf.setScanDate(LocalDate.now().toString());

        int index = 0;
        int total = Math.max(1, detections.size());
        for (Detection detection : detections) {
            index++;
            // Title and box from the VLM; the user fills in author/ISBN/etc
            shelf.addBook(new Book(detection.getRawTitle(), detection.getBbox()));
            int percent = 60 + (int) (40.0 * index / total);
            report(listener, percent, "Added “" + detection.getRawTitle() + "”");
        }

        report(listener, 100, String.format("Done — %d book(s). VLM %.1fs.",
                shelf.getBooks().size(), vlmMs / 1000.0));
        return shelf;
    }

    // The position of a box, I found that if the box is not drawn it will get a very very big x value so it should be
    // at last
    private static int centreX(Detection d) {
        Rectangle b = d.getBbox();
        return b == null ? Integer.MAX_VALUE : b.x + b.width / 2;
    }

    // We will get the median of the box height and use that as the overall height of the books box
    private static void applySingleRowLayout(List<Detection> detections, int origW, int origH) {
        List<Detection> boxed = new ArrayList<>();
        for (Detection d : detections) {
            if (d.getBbox() != null) {
                boxed.add(d);
            }
        }
        int n = boxed.size();
        if (n == 0) {
            return;
        }
        // Sort them!
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

        int top = clampInt(median(tops), 0, origH);
        int bottom = clampInt(median(bottoms), 0, origH);
        if (bottom - top < origH * 0.30) {
            // In case it is too thin we will use a fixed height instead of the median
            int mid = (top + bottom) / 2;
            int half = (int) (origH * 0.40);
            top = clampInt(mid - half, 0, origH);
            bottom = clampInt(mid + half, 0, origH);
        }
        int height = Math.max(1, bottom - top);

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
