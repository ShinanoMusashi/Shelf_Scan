// Author: Ruiqi Huang
// Description: placeholder for the archive browser For now it just reports what's saved
package shelfscape.view;

import java.awt.BorderLayout;
import java.awt.Frame;

import javax.swing.JDialog;
import javax.swing.JLabel;

import shelfscape.model.Archive;

public class ArchiveBrowser extends JDialog {

    private static final long serialVersionUID = 1L;

    public ArchiveBrowser(Frame owner, Archive archive) {
        super(owner, "Archive Browser", true);   // modal dialog
        setLayout(new BorderLayout());
        // Show a quick summary of what's in the archive (HTML for line breaks).
        String msg = String.format(
                "<html><div style='text-align:center'>Not implemented yet :(, will do in future.<br><br>"
                + "Saved: %d shelf/shelves, %d book(s).<br>"
                + "The most recent shelf is restored automatically on startup.</div></html>",
                archive.shelfCount(), archive.bookCount());
        add(new JLabel(msg, JLabel.CENTER), BorderLayout.CENTER);
        setSize(520, 300);
        setLocationRelativeTo(owner);
    }
}
