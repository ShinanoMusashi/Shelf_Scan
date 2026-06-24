// Author: Ruiqi Huang
// Description: the main window.
package shelfscape.view;

import java.awt.BorderLayout;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JToolBar;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.filechooser.FileNameExtensionFilter;

import shelfscape.controller.ScanController;
import shelfscape.model.Archive;
import shelfscape.model.Book;
import shelfscape.model.Shelf;
import shelfscape.service.ArchiveStore;
import shelfscape.service.MiniCpmVRecognizer;
import shelfscape.util.Exif;

public class MainFrame extends JFrame {

    private static final long serialVersionUID = 1L;

    // Where the archive is saved (in the working directory).
    private static final File ARCHIVE_FILE = new File("shelfscape_archive.txt");

    private Archive archive = new Archive();
    // Drives a scan using the MiniCPM-V recogniser.
    private final ScanController scanController =
            new ScanController(new MiniCpmVRecognizer());

    private final ShelfPanel shelfPanel = new ShelfPanel();
    private final BookDetailPanel detailPanel = new BookDetailPanel();
    private final JLabel statusBar = new JLabel(" Ready");
    private final JButton viewToggle = new JButton("Bookshelf View");

    // Reused so the file chooser reopens in the last-used folder.
    private final JFileChooser imageChooser = buildImageChooser();

    // Name + path of the loaded photo (path lets us reload it on startup).
    private String currentImageName = "shelf";
    private String currentImagePath = "";

    public MainFrame() {
        super("ShelfScape — Visual Bookshelf Scanner & Archive");
        archive = loadArchive();
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                saveArchive(false);
                // persist on exit
            }
        });
        setJMenuBar(buildMenuBar());
        add(buildToolBar(), BorderLayout.NORTH);
        add(new JScrollPane(shelfPanel), BorderLayout.CENTER);
        add(detailPanel, BorderLayout.EAST);
        add(statusBar, BorderLayout.SOUTH);
        shelfPanel.setSelectionListener(detailPanel::showBook);
        detailPanel.setOnSaved(() -> {
            shelfPanel.repaint();
            // a title edit changes the spine label
            saveArchive(false);
            // persist edited book info immediately
            updateStatus();
        });
        setSize(1000, 640);
        setLocationRelativeTo(null);
        restoreLastShelf();
        updateStatus();
    }

    // Load the archive from disk; warn (but carry on empty) if it can't be read.
    private Archive loadArchive() {
        try {
            return ArchiveStore.load(ARCHIVE_FILE);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this,
                    "Couldn't read the saved archive:\n" + e.getMessage(),
                    "Load failed", JOptionPane.WARNING_MESSAGE);
            return new Archive();
        }
    }

    // Save the archive. announce=true pops a confirmation (used by the menu item).
    private void saveArchive(boolean announce) {
        try {
            ArchiveStore.save(archive, ARCHIVE_FILE);
            if (announce) {
                JOptionPane.showMessageDialog(this,
                        "Saved " + archive.bookCount() + " book(s) to "
                                + ARCHIVE_FILE.getName(),
                        "Saved", JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this,
                    "Couldn't save the archive:\n" + e.getMessage(),
                    "Save failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    // On startup, redisplay the most recent shelf (reloading its photo from disk).
    private void restoreLastShelf() {
        // Highest id wins: ids are time-based, so that's the newest shelf.
        Shelf latest = null;
        for (Shelf s : archive.getShelves().values()) {
            if (latest == null || s.getId().compareTo(latest.getId()) > 0) {
                latest = s;
            }
        }
        if (latest == null) {
            return;
        }
        String path = latest.getSourceImagePath();
        if (path == null || path.isEmpty()) {
            return;
            // books are still in the archive, just no photo to draw on
        }
        File f = new File(path);
        if (!f.canRead()) {
            return;
        }
        try {
            BufferedImage img = ImageIO.read(f);
            if (img != null) {
                img = Exif.applyOrientation(img, Exif.readOrientation(f));
                currentImageName = latest.getName();
                currentImagePath = path;
                shelfPanel.setSourceImage(img);
                shelfPanel.setShelf(latest);
            }
        } catch (IOException ignored) {
            // photo moved/deleted — leave the panel empty, archive still loaded
        }
    }

    // Build the File / Scan / Archive menus.
    private JMenuBar buildMenuBar() {
        JMenuBar bar = new JMenuBar();

        JMenu file = new JMenu("File");
        JMenuItem openPhoto = new JMenuItem("Open Photo…");
        JMenuItem openArchive = new JMenuItem("Open Archive…");
        JMenuItem save = new JMenuItem("Save Archive");
        JMenuItem exit = new JMenuItem("Exit");
        openPhoto.addActionListener(e -> onOpenPhoto());
        openArchive.addActionListener(e -> onOpenArchive());
        save.addActionListener(e -> saveArchive(true));
        exit.addActionListener(e -> dispose());
        file.add(openPhoto);
        file.add(openArchive);
        file.add(save);
        file.addSeparator();
        file.add(exit);

        JMenu scan = new JMenu("Scan");
        JMenuItem scanShelf = new JMenuItem("Scan Whole Shelf");
        JMenuItem scanRegion = new JMenuItem("Scan Region");
        scanShelf.addActionListener(e -> onScanShelf());
        scanRegion.addActionListener(e -> onScanRegion());
        scan.add(scanShelf);
        scan.add(scanRegion);

        JMenu archiveMenu = new JMenu("Archive");
        JMenuItem browse = new JMenuItem("Open Archive Browser…");
        browse.addActionListener(e -> onOpenArchive());
        archiveMenu.add(browse);

        bar.add(file);
        bar.add(scan);
        bar.add(archiveMenu);
        return bar;
    }

    // Build the toolbar (same actions as the menus, plus the view toggle).
    private JToolBar buildToolBar() {
        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);

        JButton openPhoto = new JButton("Open Photo");
        JButton scanShelf = new JButton("Scan Shelf");
        JButton scanRegion = new JButton("Scan Region");
        JButton openArchive = new JButton("Open Archive");
        JButton manageTags = new JButton("Manage Tags");
        openPhoto.setToolTipText("Pick a photo and display it (does not scan yet)");
        scanShelf.setToolTipText("Scan the whole loaded photo");
        scanRegion.setToolTipText("Drag a rectangle on the photo first, then scan just that area at higher detail");
        viewToggle.setToolTipText("Switch between the scanned photo and a tidy bookshelf view");
        openPhoto.addActionListener(e -> onOpenPhoto());
        scanShelf.addActionListener(e -> onScanShelf());
        scanRegion.addActionListener(e -> onScanRegion());
        viewToggle.addActionListener(e -> onToggleView());
        openArchive.addActionListener(e -> onOpenArchive());
        manageTags.addActionListener(e -> notImplemented("Manage Tags"));
        toolBar.add(openPhoto);
        toolBar.add(scanShelf);
        toolBar.add(scanRegion);
        toolBar.addSeparator();
        toolBar.add(viewToggle);
        toolBar.addSeparator();
        toolBar.add(openArchive);
        toolBar.add(manageTags);

        return toolBar;
    }

    // Load a photo and display it. Does NOT scan — the user scans when ready.
    private void onOpenPhoto() {
        BufferedImage photo = chooseAndLoadImage();
        if (photo == null) {
            return;
            // cancelled or failed (a dialog was already shown)
        }
        File file = imageChooser.getSelectedFile();
        currentImageName = file.getName();
        currentImagePath = file.getAbsolutePath();
        shelfPanel.setShelf(null);
        shelfPanel.clearSelection();
        shelfPanel.setSourceImage(photo);
        detailPanel.showBook(null);
        statusBar.setText(String.format(
                " Loaded %s (%d × %d px) — drag a region and “Scan Region”, or “Scan Shelf”.",
                file.getName(), photo.getWidth(), photo.getHeight()));
    }

    // Scan the whole currently-loaded photo.
    private void onScanShelf() {
        BufferedImage full = shelfPanel.getSourceImage();
        if (full == null) {
            JOptionPane.showMessageDialog(this,
                    "Open a photo with “Open Photo” first.",
                    "No photo", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        Integer rotation = askSpineOrientation();
        if (rotation == null) {
            return;
        }
        runScan(full, full, new Point(0, 0), currentImageName, rotation);
    }

    // Scan only the dragged region. The photo is downscaled to ~1024 px before
    // sending, so a whole shelf loses fine text, a crop sends that area at much
    // higher detail. Boxes are offset back onto the full photo afterwards.
    private void onScanRegion() {
        BufferedImage full = shelfPanel.getSourceImage();
        if (full == null) {
            JOptionPane.showMessageDialog(this,
                    "Open a photo with “Open Photo” first.",
                    "No photo", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        Rectangle region = shelfPanel.getSelectionImageRect();
        if (region == null || region.width < 20 || region.height < 20) {
            JOptionPane.showMessageDialog(this,
                    "Drag a rectangle over the area you want to scan, then click “Scan Region”.",
                    "Select a region first", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        Integer rotation = askSpineOrientation();
        if (rotation == null) {
            return;
        }
        BufferedImage crop = full.getSubimage(region.x, region.y, region.width, region.height);
        runScan(crop, full, new Point(region.x, region.y),
                currentImageName + " (region)", rotation);
    }

    // Ask which way the spines face. Returns the clockwise rotation that makes the
    // titles upright for the model, or null if the user dismissed the dialog.
    private Integer askSpineOrientation() {
        String[] labels = {
                "Upright (titles read left → right)",
                "Rotate 90° left  ↺",
                "Rotate 90° right ↻",
        };
        int[] degrees = { 0, 270, 90 };
        int choice = JOptionPane.showOptionDialog(this,
                "How are the book spines oriented?\n"
                        + "Pick the rotation that would make the titles read normally.\n"
                        + "(If the boxes look wrong afterwards, try the opposite rotation.)",
                "Spine orientation",
                JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
                null, labels, labels[0]); // default highlight = Upright
        if (choice < 0) {
            return null;
        }
        return degrees[choice];
    }

    // Run the scan on a background worker (off the Swing thread), updating the
    // dialog via invokeLater / done().
    private void runScan(BufferedImage scanImage, BufferedImage displayImage,
            Point offset, String name, int rotationCw) {
        ScanDialog dialog = new ScanDialog(this);
        dialog.setPhoto(scanImage, name);

        SwingWorker<Shelf, Void> worker = new SwingWorker<Shelf, Void>() {
            @Override
            protected Shelf doInBackground() throws Exception {
                ScanController.ProgressListener listener = (percent, message) ->
                        SwingUtilities.invokeLater(() -> {
                            if (percent >= 0) {
                                dialog.setProgress(percent, "Working…");
                            }
                            dialog.appendLog(message);
                        });
                return scanController.scanShelf(scanImage, name, rotationCw, listener);
            }

            @Override
            protected void done() {
                dialog.setCancelEnabled(false);
                if (isCancelled()) {
                    dialog.appendLog("Cancelled.");
                    return;
                }
                try {
                    // Success: finalise the shelf, show it, and save.
                    Shelf shelf = get();
                    offsetBoxes(shelf, offset);
                    shelf.setSourceImagePath(currentImagePath);
                    archive.addShelf(shelf);
                    shelfPanel.clearSelection();
                    shelfPanel.setSourceImage(displayImage);
                    shelfPanel.setShelf(shelf);
                    saveArchive(false); // persist the new shelf right away
                    updateStatus();
                    dialog.appendLog("Added to archive and saved.");
                    dialog.dispose();
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    dialog.setProgress(0, "Failed");
                    dialog.appendLog("ERROR: " + cause.getMessage());
                    JOptionPane.showMessageDialog(dialog, friendlyError(cause),
                            "Scan failed", JOptionPane.ERROR_MESSAGE);
                }
            }
        };

        dialog.setOnCancel(() -> worker.cancel(true));
        worker.execute();
        dialog.setVisible(true);
        // modal: a nested event loop keeps the EDT pumping
    }

    // Shift every box by offset so a cropped scan lines up with the full photo.
    private static void offsetBoxes(Shelf shelf, Point offset) {
        if (offset.x == 0 && offset.y == 0) {
            return;
        }
        for (Book book : shelf.getBooks()) {
            Rectangle r = book.getBbox();
            if (r != null) {
                book.setBbox(new Rectangle(r.x + offset.x, r.y + offset.y, r.width, r.height));
            }
        }
    }

    // Turn a low-level exception into a message the user can act on.
    private static String friendlyError(Throwable cause) {
        if (cause instanceof java.net.ConnectException) {
            return "Couldn't reach Ollama at localhost:11434.\n"
                    + "Check if 'ollama serve' is running and 'minicpm-v' is pulled.";
        }
        if (cause instanceof java.net.SocketTimeoutException) {
            return "MiniCPM-V didn't responding :(.\n\n"
                    + "The model may still be loading (the first scan is slowest), or a\n"
                    + "previous scan is still running. Wait a moment and try again — it's\n"
                    + "usually much faster the second time. A smaller photo also helps.";
        }
        return "The scan failed:\n" + cause.getMessage();
    }

    // Show the chooser, validate the choice, and load it (EXIF-corrected) into a
    // BufferedImage. Returns null on cancel or a bad file (with an error dialog).
    private BufferedImage chooseAndLoadImage() {
        if (imageChooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return null;
        }
        File file = imageChooser.getSelectedFile();
        if (file == null || !file.canRead()) {
            JOptionPane.showMessageDialog(this,
                    "That file can't be read. Pick a .jpg, .jpeg or .png photo.",
                    "Cannot read file", JOptionPane.ERROR_MESSAGE);
            return null;
        }
        try {
            BufferedImage image = ImageIO.read(file);
            if (image == null) {
                // ImageIO returns null when the format isn't a recognised image.
                JOptionPane.showMessageDialog(this,
                        "That doesn't look like a supported image (.jpg, .jpeg, .png).",
                        "Unsupported image", JOptionPane.ERROR_MESSAGE);
                return null;
            }
            // ImageIO ignores the EXIF rotation flag, so phone photos come out
            // sideways/upside-down. Rotate to match how the camera intended it.
            return Exif.applyOrientation(image, Exif.readOrientation(file));
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this,
                    "Could not load the image:\n" + ex.getMessage(),
                    "Load failed", JOptionPane.ERROR_MESSAGE);
            return null;
        }
    }

    // Make the image file chooser (jpg/jpeg/png only).
    private static JFileChooser buildImageChooser() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Choose a bookshelf photo");
        chooser.setAcceptAllFileFilterUsed(false);
        chooser.setFileFilter(new FileNameExtensionFilter(
                "Images (*.jpg, *.jpeg, *.png)", "jpg", "jpeg", "png"));
        return chooser;
    }

    // Flip between the scanned-photo view and the bookshelf view (and the label).
    private void onToggleView() {
        if (shelfPanel.getViewMode() == ShelfPanel.ViewMode.SCAN) {
            shelfPanel.setViewMode(ShelfPanel.ViewMode.SHELF);
            viewToggle.setText("Scan View");
        } else {
            shelfPanel.setViewMode(ShelfPanel.ViewMode.SCAN);
            viewToggle.setText("Bookshelf View");
        }
    }

    // Open the (placeholder) archive browser.
    private void onOpenArchive() {
        new ArchiveBrowser(this, archive).setVisible(true);
    }

    // Generic "coming soon" dialog for buttons that aren't built yet.
    private void notImplemented(String feature) {
        JOptionPane.showMessageDialog(this,
                feature + " is not implemented yet.",
                "Coming soon", JOptionPane.INFORMATION_MESSAGE);
    }

    // Refresh the status bar counts.
    private void updateStatus() {
        statusBar.setText(String.format(
                " Shelves: %d   Books: %d",
                archive.shelfCount(), archive.bookCount()));
    }
}
