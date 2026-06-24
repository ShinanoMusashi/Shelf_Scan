// Author: Ruiqi Huang
// Description: the main canvas. Two modes — SCAN draws boxes over the photo, SHELF draws a stylised bookshelf. Handles
// mouse interaction and hit-testing, but doesn't know about the scanning process or how the shelf is built.
package shelfscape.view;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.swing.JPanel;

import shelfscape.model.Book;
import shelfscape.model.Shelf;

public class ShelfPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    // Colours cycled across books so neighbours stand out.
    private static final Color[] PALETTE = {
            new Color(0xE6, 0x55, 0x4D), new Color(0x4D, 0x96, 0xE6),
            new Color(0x5C, 0xB8, 0x5C), new Color(0xE6, 0xA5, 0x4D),
            new Color(0x9B, 0x5C, 0xE6), new Color(0x4D, 0xC4, 0xC4),
    };

    // A drag shorter than this (px) counts as a click, not a region selection.
    private static final int DRAG_THRESHOLD = 8;

    // The two ways of drawing: boxes over the photo, or a stylised bookshelf.
    public enum ViewMode { SCAN, SHELF }

    private ViewMode mode = ViewMode.SCAN;

    private Shelf shelf;
    private BufferedImage sourceImage;
    private Book selectedBook;

    private Point dragStart;
    private Rectangle selectionRect;

    // Fired when the user clicks a book
    public interface SelectionListener {
        void onBookSelected(Book book);
    }

    private SelectionListener selectionListener;

    public ShelfPanel() {
        setBackground(new Color(0x2B, 0x2B, 0x2B));
        setPreferredSize(new Dimension(700, 500));
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                dragStart = e.getPoint();
                selectionRect = null;
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                // Region selection only applies to scan view.
                if (dragStart != null && mode == ViewMode.SCAN) {
                    selectionRect = normalizeRect(dragStart, e.getPoint());
                    repaint();
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (dragStart == null) {
                    return;
                }
                int dx = Math.abs(e.getX() - dragStart.x);
                int dy = Math.abs(e.getY() - dragStart.y);
                if (mode == ViewMode.SHELF || (dx < DRAG_THRESHOLD && dy < DRAG_THRESHOLD)) {
                    // Barely moved → treat as a click: select the book under the cursor.
                    selectionRect = null;
                    Book hit = hitTest(e.getPoint());
                    setSelectedBook(hit);
                    if (selectionListener != null) {
                        selectionListener.onBookSelected(hit);
                    }
                } else {
                    selectionRect = normalizeRect(dragStart, e.getPoint());
                    repaint();
                }
                dragStart = null;
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
    }

    // Build a normalised rectangle from two drag points.
    private static Rectangle normalizeRect(Point a, Point b) {
        return new Rectangle(Math.min(a.x, b.x), Math.min(a.y, b.y),
                Math.abs(a.x - b.x), Math.abs(a.y - b.y));
    }

    public void setSelectionListener(SelectionListener listener) {
        this.selectionListener = listener;
    }

    // Switch between the photo+boxes view and the bookshelf view.
    public void setViewMode(ViewMode mode) {
        this.mode = mode;
        this.selectionRect = null;
        repaint();
    }

    public ViewMode getViewMode() {
        return mode;
    }

    // Set the shelf to render; clears the selection.
    public void setShelf(Shelf shelf) {
        this.shelf = shelf;
        this.selectedBook = null;
        repaint();
    }

    // Set the backdrop photo (also the basis for the box layout transform).
    public void setSourceImage(BufferedImage image) {
        this.sourceImage = image;
        repaint();
    }

    public BufferedImage getSourceImage() {
        return sourceImage;
    }

    public void setSelectedBook(Book book) {
        this.selectedBook = book;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        if (mode == ViewMode.SHELF) {
            if (shelf == null || shelf.getBooks().isEmpty()) {
                drawMessage(g2, "Scan a shelf first, then switch to bookshelf view :)");
            } else {
                drawBookshelf(g2);
            }
            g2.dispose();
            return;
        }

        if (sourceImage == null) {
            drawMessage(g2, "No shelf loaded, use Open Photo, then Scan.");
            g2.dispose();
            return;
        }

        drawFittedImage(g2);
        if (shelf != null) {
            drawBooks(g2);
        }
        if (selectionRect != null) {
            drawSelection(g2);
        }
        g2.dispose();
    }

    private static final int SHELF_MARGIN = 24;
    private static final int SHELF_GAP = 5;     // gap between spines in a row
    private static final int SHELF_BOARD = 16;  // thickness of a shelf board
    private static final int ROW_GAP = 12;      // vertical gap between rows

    // One laid-out spine: which book, where it's drawn, and which row it's in.
    private static final class Spine {
        final Book book;
        final Rectangle rect;
        final int row;

        Spine(Book book, Rectangle rect, int row) {
            this.book = book;
            this.rect = rect;
            this.row = row;
        }
    }

    private void drawBookshelf(Graphics2D g2) {
        // Warm "wall" backdrop.
        g2.setColor(new Color(0x3A, 0x30, 0x28));
        g2.fillRect(0, 0, getWidth(), getHeight());

        List<Spine> spines = layoutSpines();

        // Draw a board under each row (board top = the lowest spine bottom in it).
        Map<Integer, Integer> rowBaseline = new TreeMap<>();
        for (Spine s : spines) {
            rowBaseline.merge(s.row, s.rect.y + s.rect.height, Math::max);
        }
        for (int baseline : rowBaseline.values()) {
            g2.setColor(new Color(0x6B, 0x47, 0x2F));
            g2.fillRect(SHELF_MARGIN - 8, baseline, getWidth() - 2 * SHELF_MARGIN + 16, SHELF_BOARD);
            g2.setColor(new Color(0x4A, 0x30, 0x20));
            g2.drawRect(SHELF_MARGIN - 8, baseline, getWidth() - 2 * SHELF_MARGIN + 16, SHELF_BOARD);
        }

        // Draw the spines.
        for (int i = 0; i < spines.size(); i++) {
            Spine s = spines.get(i);
            Rectangle r = s.rect;
            Color base = PALETTE[i % PALETTE.length];
            boolean selected = s.book == selectedBook;

            g2.setColor(base);
            g2.fillRect(r.x, r.y, r.width, r.height);
            // A subtle darker strip near the spine edge for a book-ish look.
            g2.setColor(base.darker());
            g2.fillRect(r.x, r.y, r.width, 4);
            g2.fillRect(r.x, r.y + r.height - 4, r.width, 4);

            g2.setColor(selected ? Color.YELLOW : base.darker().darker());
            g2.setStroke(new BasicStroke(selected ? 3f : 1.5f));
            g2.drawRect(r.x, r.y, r.width, r.height);

            drawSpineLabel(g2, r, s.book.getTitle());
        }
    }

    // Lay every book out as a spine, stacking the shelf rows top-to-bottom. Within
    // a row the books keep their left-to-right order; heights vary a little per
    // title so it looks natural.
    private List<Spine> layoutSpines() {
        List<Spine> out = new ArrayList<>();
        if (shelf == null) {
            return out;
        }
        List<List<Book>> rows = booksByRow();
        int rowCount = rows.size();
        if (rowCount == 0) {
            return out;
        }
        int availW = getWidth() - 2 * SHELF_MARGIN;
        int totalH = getHeight() - 2 * SHELF_MARGIN - (rowCount - 1) * ROW_GAP;
        if (availW <= 0 || totalH <= 0) {
            return out;
        }
        int rowH = totalH / rowCount;            // height per row, incl. its board
        int spineMaxH = Math.max(1, rowH - SHELF_BOARD);

        for (int r = 0; r < rowCount; r++) {
            List<Book> rowBooks = rows.get(r);
            int n = rowBooks.size();
            if (n == 0) {
                continue;
            }
            int rowTop = SHELF_MARGIN + r * (rowH + ROW_GAP);
            int baseline = rowTop + rowH - SHELF_BOARD;
            int spineW = Math.max(6, (availW - (n - 1) * SHELF_GAP) / n);
            for (int i = 0; i < n; i++) {
                int x = SHELF_MARGIN + i * (spineW + SHELF_GAP);
                int h = spineHeight(rowBooks.get(i), spineMaxH);
                out.add(new Spine(rowBooks.get(i), new Rectangle(x, baseline - h, spineW, h), r));
            }
        }
        return out;
    }

    // Group the shelf's books by row index, rows in ascending order, keeping each
    // row's existing left-to-right order.
    private List<List<Book>> booksByRow() {
        Map<Integer, List<Book>> byRow = new TreeMap<>();
        for (Book b : shelf.getBooks()) {
            byRow.computeIfAbsent(b.getRow(), k -> new ArrayList<>()).add(b);
        }
        return new ArrayList<>(byRow.values());
    }

    // A repeatable 72-100% of the available height, varied by the title's hash.
    private static int spineHeight(Book book, int availH) {
        String title = book.getTitle() == null ? "" : book.getTitle();
        int hash = Math.abs(title.hashCode());
        double frac = 0.72 + (hash % 29) / 100.0; // 0.72 .. 1.00
        return (int) Math.round(availH * frac);
    }

    private void drawSelection(Graphics2D g2) {
        g2.setColor(new Color(255, 255, 255, 40));
        g2.fillRect(selectionRect.x, selectionRect.y, selectionRect.width, selectionRect.height);
        g2.setColor(Color.WHITE);
        g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                1f, new float[] { 6f, 4f }, 0f));
        g2.drawRect(selectionRect.x, selectionRect.y, selectionRect.width, selectionRect.height);
    }

    private void drawFittedImage(Graphics2D g2) {
        double[] t = transform();
        if (t == null) {
            return;
        }
        int dw = (int) Math.round(sourceImage.getWidth() * t[0]);
        int dh = (int) Math.round(sourceImage.getHeight() * t[0]);
        g2.drawImage(sourceImage, (int) Math.round(t[1]), (int) Math.round(t[2]), dw, dh, null);
    }

    private void drawBooks(Graphics2D g2) {
        List<Book> books = shelf.getBooks();
        for (int i = 0; i < books.size(); i++) {
            Book book = books.get(i);
            Rectangle r = imageToPanel(book.getBbox());
            if (r == null) {
                continue; // book without a location (no bbox) can't be drawn
            }
            Color base = PALETTE[i % PALETTE.length];
            boolean selected = book == selectedBook;

            // Translucent fill so the photo shows through.
            Composite original = g2.getComposite();
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, selected ? 0.45f : 0.28f));
            g2.setColor(base);
            g2.fillRect(r.x, r.y, r.width, r.height);
            g2.setComposite(original);

            g2.setColor(selected ? Color.YELLOW : base.brighter());
            g2.setStroke(new BasicStroke(selected ? 3f : 1.5f));
            g2.drawRect(r.x, r.y, r.width, r.height);

            drawSpineLabel(g2, r, book.getTitle());
        }
    }

    // Draw the title inside the box — rotated for tall spines, flat for wide ones.
    private void drawSpineLabel(Graphics2D g2, Rectangle r, String title) {
        if (title == null || title.isEmpty()) {
            return;
        }
        g2.setColor(Color.WHITE);
        g2.setFont(getFont().deriveFont(Font.BOLD, 12f));
        Graphics2D t2 = (Graphics2D) g2.create();
        if (r.height >= r.width) {
            // Vertical spine: rotate text 90° clockwise, reading top→bottom.
            t2.translate(r.x + r.width / 2.0 + 5, r.y + 6);
            t2.rotate(Math.PI / 2);
            t2.drawString(clip(t2, title, r.height - 12), 0, 0);
        } else {
            t2.drawString(clip(t2, title, r.width - 8), r.x + 4, r.y + r.height / 2 + 4);
        }
        t2.dispose();
    }

    // Trim text with an ellipsis so it fits within maxPx.
    private static String clip(Graphics2D g2, String text, int maxPx) {
        if (maxPx <= 0) {
            return "";
        }
        if (g2.getFontMetrics().stringWidth(text) <= maxPx) {
            return text;
        }
        String ell = "…";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            if (g2.getFontMetrics().stringWidth(sb.toString() + text.charAt(i) + ell) > maxPx) {
                break;
            }
            sb.append(text.charAt(i));
        }
        return sb + ell;
    }

    // Centre a grey message (used when there's nothing to draw).
    private void drawMessage(Graphics2D g2, String msg) {
        g2.setColor(new Color(0x88, 0x88, 0x88));
        g2.setFont(getFont().deriveFont(Font.PLAIN, 16f));
        int x = (getWidth() - g2.getFontMetrics().stringWidth(msg)) / 2;
        int y = getHeight() / 2;
        g2.drawString(msg, Math.max(10, x), y);
    }

    // The image->panel transform as {scale, offsetX, offsetY}: fit the photo into
    // the panel keeping aspect ratio. Null when there's no image.
    private double[] transform() {
        if (sourceImage == null) {
            return null;
        }
        int pw = getWidth();
        int ph = getHeight();
        int iw = sourceImage.getWidth();
        int ih = sourceImage.getHeight();
        if (iw <= 0 || ih <= 0) {
            return null;
        }
        double scale = Math.min((double) pw / iw, (double) ph / ih);
        double ox = (pw - iw * scale) / 2.0;
        double oy = (ph - ih * scale) / 2.0;
        return new double[] { scale, ox, oy };
    }

    // The drag selection mapped to source-image pixels (clamped to the image),
    // or null if nothing is selected. Used by "Scan Region".
    public Rectangle getSelectionImageRect() {
        if (selectionRect == null || sourceImage == null) {
            return null;
        }
        double[] t = transform();
        if (t == null) {
            return null;
        }
        int ix = (int) Math.round((selectionRect.x - t[1]) / t[0]);
        int iy = (int) Math.round((selectionRect.y - t[2]) / t[0]);
        int iw = (int) Math.round(selectionRect.width / t[0]);
        int ih = (int) Math.round(selectionRect.height / t[0]);
        Rectangle bounds = new Rectangle(0, 0, sourceImage.getWidth(), sourceImage.getHeight());
        return new Rectangle(ix, iy, iw, ih).intersection(bounds);
    }

    // Clear the drag selection.
    public void clearSelection() {
        selectionRect = null;
        repaint();
    }

    // Map a box from source-image pixels to panel pixels.
    private Rectangle imageToPanel(Rectangle box) {
        if (box == null) {
            return null;
        }
        double[] t = transform();
        if (t == null) {
            return null;
        }
        return new Rectangle(
                (int) Math.round(t[1] + box.x * t[0]),
                (int) Math.round(t[2] + box.y * t[0]),
                (int) Math.round(box.width * t[0]),
                (int) Math.round(box.height * t[0]));
    }

    // Topmost book under point p, or null. Walks back-to-front (last drawn wins),
    // using the spine layout in SHELF mode and the boxes in SCAN mode.
    public Book hitTest(Point p) {
        if (shelf == null) {
            return null;
        }
        if (mode == ViewMode.SHELF) {
            List<Spine> spines = layoutSpines();
            for (int i = spines.size() - 1; i >= 0; i--) {
                if (spines.get(i).rect.contains(p)) {
                    return spines.get(i).book;
                }
            }
            return null;
        }
        List<Book> books = shelf.getBooks();
        for (int i = books.size() - 1; i >= 0; i--) {
            Book book = books.get(i);
            Rectangle r = imageToPanel(book.getBbox());
            if (r != null && r.contains(p)) {
                return book;
            }
        }
        return null;
    }
}
