// Author: Ruiqi Huang
// Description: decide how many tiles a shelf row must be split into so the model
// can read small spine text, and produce those tile rectangles. Pure geometry.
package shelfscape.scan;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

public final class TilePlanner {

    private TilePlanner() {
        // Ignore this
    }

    // Long side we downscale a sent image to (matches MiniCpmVRecognizer.MAX_SIDE).
    public static final int SENT_LONG_SIDE = 1024;
    // We want each book to appear at least this wide in a sent tile to stay legible.
    private static final int TARGET_BOOK_PX = 120;
    // Never explode one row into more than this many tiles (bounds scan time).
    private static final int MAX_TILES_PER_ROW = 6;
    // Tiles overlap by this fraction of a tile so a book on a seam isn't cut in half.
    private static final double OVERLAP = 0.12;

    // How many horizontal tiles this row needs. Adaptive: driven by how many books
    // were detected (density) and by the row width (so we never downscale below
    // native resolution). Returns >= 1.
    public static int tilesForRow(int bookCount, int rowWidthPx) {
        // Enough tiles that each detected book gets ~TARGET_BOOK_PX in a sent tile.
        int byDensity = (int) Math.ceil(Math.max(1, bookCount) * (double) TARGET_BOOK_PX / SENT_LONG_SIDE);
        // Enough tiles that no tile is downscaled (keeps all native detail).
        int byResolution = (int) Math.ceil((double) rowWidthPx / SENT_LONG_SIDE);
        int tiles = Math.max(byDensity, byResolution);
        return Math.max(1, Math.min(MAX_TILES_PER_ROW, tiles));
    }

    // Split a band (full-width row strip) into n overlapping horizontal tiles.
    public static List<Rectangle> horizontalTiles(Rectangle band, int n) {
        List<Rectangle> tiles = new ArrayList<>();
        if (n <= 1) {
            tiles.add(new Rectangle(band));
            return tiles;
        }
        double step = (double) band.width / n;
        int overlapPx = (int) Math.round(step * OVERLAP);
        for (int i = 0; i < n; i++) {
            // Extend inward edges by the overlap so seams are covered twice.
            int x0 = band.x + (int) Math.round(i * step) - (i > 0 ? overlapPx : 0);
            int x1 = band.x + (int) Math.round((i + 1) * step) + (i < n - 1 ? overlapPx : 0);
            x0 = Math.max(band.x, x0);
            x1 = Math.min(band.x + band.width, x1);
            tiles.add(new Rectangle(x0, band.y, Math.max(1, x1 - x0), band.height));
        }
        return tiles;
    }
}
