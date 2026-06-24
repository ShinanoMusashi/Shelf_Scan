// Project: ShelfScape
// Author: Ruiqi Huang
// Course: ICS4U1-02
// Date: June 1, 2026
//
// Description:
// This is an app which it takes a photo of a bookshelf (it's better if there's only one row cause it makes the characters
// more clear, then the photo goes through a local OCR model (which in this case I use miniCPM-V, it runs the fastest
// on these smaller computers, this OCR is run through Ollama, this requires a local setup for the environment, detailed
// steps are in the NOTICE file, if unable to set up properly, there's a fall back to an image that has already run, which
// will give out a bookshelf to test out the bookshelf feature), after successfully generating the titles of the book,
// it will generate a bookshelf out of it. The user can input the book's info and save it.
//
// Third-party component (the part I did NOT make are listed in NOTICE.md):
//   - MiniCPM-V vision-language model (OpenBMB), run using Ollama.
//   Everything under src/shelfscape/ is my own work.
package shelfscape.app;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import shelfscape.view.MainFrame;

// Entry point. Build the main window
public final class MainApp {

    private MainApp() {
        // Placeholder (don't do anything)
    }

    public static void main(String[] args) {
        // Event Dispatch Thread
        SwingUtilities.invokeLater(() -> {
            try {
                // This setting will auto make the feel and look of the app to the system the user is on
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // in case it does not work
            }
            new MainFrame().setVisible(true);
        });
    }
}
