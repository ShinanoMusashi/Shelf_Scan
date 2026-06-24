// Author: Ruiqi Huang
// Description: the modal dialog shown while a scan runs
package shelfscape.view;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Image;
import java.awt.image.BufferedImage;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

public class ScanDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    private static final int THUMB_MAX = 160;
    // max thumbnail side in px

    private final JLabel thumbnail = new JLabel("(no photo)", JLabel.CENTER);
    private final JLabel pathLabel = new JLabel(" ");
    private final JProgressBar progressBar = new JProgressBar();
    private final JTextArea log = new JTextArea(8, 40);
    private final JButton cancelButton = new JButton("Cancel");

    // Optional action run when Cancel is pressed
    private Runnable onCancel;

    public ScanDialog(Frame owner) {
        super(owner, "New Scan", true);
        // modal
        setLayout(new BorderLayout(8, 8));

        add(buildHeader(), BorderLayout.NORTH);

        log.setEditable(false);
        add(new JScrollPane(log), BorderLayout.CENTER);

        JPanel south = new JPanel();
        south.add(cancelButton);
        add(south, BorderLayout.SOUTH);

        // Cancel: run the hook then close.
        cancelButton.addActionListener(e -> {
            if (onCancel != null) {
                onCancel.run();
            }
            dispose();
        });

        setPreferredSize(new Dimension(460, 420));
        pack();
        setLocationRelativeTo(owner);
    }

    // Stack the thumbnail, path label and progress bar at the top.
    private JPanel buildHeader() {
        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setBorder(BorderFactory.createEmptyBorder(8, 8, 0, 8));

        thumbnail.setPreferredSize(new Dimension(THUMB_MAX, THUMB_MAX));
        thumbnail.setAlignmentX(CENTER_ALIGNMENT);
        pathLabel.setAlignmentX(CENTER_ALIGNMENT);

        progressBar.setStringPainted(true);
        // Start indeterminate (animated): inference time is unknown up front.
        progressBar.setIndeterminate(true);
        progressBar.setString("Working…");
        progressBar.setAlignmentX(CENTER_ALIGNMENT);

        header.add(thumbnail);
        header.add(pathLabel);
        header.add(progressBar);
        return header;
    }

    // Show a thumbnail of the chosen photo and its path.
    public void setPhoto(BufferedImage image, String path) {
        pathLabel.setText(path);
        if (image == null) {
            thumbnail.setIcon(null);
            thumbnail.setText("(no photo)");
            return;
        }
        // Scale to fit the thumbnail box, keeping aspect ratio.
        int w = image.getWidth();
        int h = image.getHeight();
        double scale = Math.min((double) THUMB_MAX / w, (double) THUMB_MAX / h);
        Image scaled = image.getScaledInstance(
                Math.max(1, (int) Math.round(w * scale)),
                Math.max(1, (int) Math.round(h * scale)),
                Image.SCALE_SMOOTH);
        thumbnail.setText(null);
        thumbnail.setIcon(new ImageIcon(scaled));
    }

    // Register an action to run when the user presses Cancel.
    public void setOnCancel(Runnable onCancel) {
        this.onCancel = onCancel;
    }

    // Disable Cancel (e.g. once the scan has finished).
    public void setCancelEnabled(boolean enabled) {
        cancelButton.setEnabled(enabled);
    }

    // Append a line to the output log.
    public void appendLog(String line) {
        log.append(line + "\n");
    }

    // Set a real percentage + stage label (switches the bar out of animated mode).
    public void setProgress(int percent, String stage) {
        progressBar.setIndeterminate(false);
        progressBar.setValue(percent);
        progressBar.setString(stage + "  " + percent + "%");
    }
}
