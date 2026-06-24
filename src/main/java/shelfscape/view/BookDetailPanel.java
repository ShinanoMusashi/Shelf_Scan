// Author: Ruiqi Huang
// Description: the right-hand panel. Shows the selected book and lets the user type in
// title/author/ISBN/year/status/notes, then Save writes them onto the book.
package shelfscape.view;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

import shelfscape.model.Book;
import shelfscape.model.ReadingStatus;

public class BookDetailPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private Book current;
    // the book being edited, or null when nothing's selected

    // Editable fields.
    private final JTextField titleField = new JTextField();
    private final JTextField authorField = new JTextField();
    private final JTextField isbnField = new JTextField();
    private final JTextField yearField = new JTextField();
    private final JComboBox<ReadingStatus> statusCombo =
            new JComboBox<>(ReadingStatus.values());
    private final JTextArea notesArea = new JTextArea(5, 18);
    private final JButton saveButton = new JButton("Save");
    private final JLabel hint = new JLabel(" ");
    // small status line ("Saved." etc.)

    // Optional callback run after a save (MainFrame uses it to repaint + persist).
    private Runnable onSaved;

    public BookDetailPanel() {
        setLayout(new BorderLayout(0, 6));
        setBorder(BorderFactory.createTitledBorder("Book details"));
        setPreferredSize(new Dimension(300, 0));

        add(buildForm(), BorderLayout.CENTER);

        // Hint line above a centered Save button at the bottom.
        JPanel south = new JPanel(new BorderLayout());
        hint.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
        south.add(hint, BorderLayout.NORTH);
        JPanel buttons = new JPanel();
        buttons.add(saveButton);
        south.add(buttons, BorderLayout.SOUTH);
        add(south, BorderLayout.SOUTH);

        saveButton.addActionListener(e -> save());
        showBook(null);
        // start empty + disabled
    }

    public void setOnSaved(Runnable onSaved) {
        this.onSaved = onSaved;
    }

    // Build the labelled form (a row per field, then a stretchy notes area).
    private JPanel buildForm() {
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 4, 3, 4);
        c.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;
        row = addRow(form, c, row, "Title", titleField);
        row = addRow(form, c, row, "Author", authorField);
        row = addRow(form, c, row, "ISBN", isbnField);
        row = addRow(form, c, row, "Year", yearField);
        row = addRow(form, c, row, "Status", statusCombo);

        // "Notes" label spanning both columns ->
        c.gridx = 0;
        c.gridy = row++;
        c.gridwidth = 2;
        c.weightx = 1;
        form.add(new JLabel("Notes"), c);

        // ->then the text area filling the remaining space.
        notesArea.setLineWrap(true);
        notesArea.setWrapStyleWord(true);
        c.gridy = row;
        c.weighty = 1;
        c.fill = GridBagConstraints.BOTH;
        form.add(new JScrollPane(notesArea), c);
        return form;
    }

    // Add one "label : field" row to the grid; returns the next row index.
    private int addRow(JPanel form, GridBagConstraints c, int row, String label,
            java.awt.Component field) {
        c.gridx = 0;
        c.gridy = row;
        c.gridwidth = 1;
        c.weightx = 0;
        form.add(new JLabel(label), c);
        c.gridx = 1;
        c.weightx = 1;
        form.add(field, c);
        return row + 1;
    }

    // Show a book's details, or clear + disable the form when null.
    public void showBook(Book book) {
        this.current = book;
        boolean has = book != null;
        setFieldsEnabled(has);
        if (!has) {
            // Nothing selected: blank everything out.
            titleField.setText("");
            authorField.setText("");
            isbnField.setText("");
            yearField.setText("");
            statusCombo.setSelectedItem(ReadingStatus.NONE);
            notesArea.setText("");
            hint.setText("Select a book on the shelf.");
            return;
        }
        // Fill the fields from the book (year 0 shows as blank).
        titleField.setText(nullToEmpty(book.getTitle()));
        authorField.setText(nullToEmpty(book.getAuthor()));
        isbnField.setText(nullToEmpty(book.getIsbn()));
        yearField.setText(book.getYear() > 0 ? String.valueOf(book.getYear()) : "");
        statusCombo.setSelectedItem(book.getStatus() == null ? ReadingStatus.NONE : book.getStatus());
        notesArea.setText(nullToEmpty(book.getDescription()));
        hint.setText(" ");
    }

    // Copy the form values back onto the current book.
    private void save() {
        if (current == null) {
            return;
        }
        current.setTitle(titleField.getText().trim());
        current.setAuthor(authorField.getText().trim());
        current.setIsbn(isbnField.getText().trim());
        current.setYear(parseYear(yearField.getText()));
        current.setStatus((ReadingStatus) statusCombo.getSelectedItem());
        current.setDescription(notesArea.getText().trim());
        hint.setText("Saved.");
        if (onSaved != null) {
            onSaved.run();
        }
    }

    // Enable/disable every input together.
    private void setFieldsEnabled(boolean enabled) {
        titleField.setEnabled(enabled);
        authorField.setEnabled(enabled);
        isbnField.setEnabled(enabled);
        yearField.setEnabled(enabled);
        statusCombo.setEnabled(enabled);
        notesArea.setEnabled(enabled);
        saveButton.setEnabled(enabled);
    }

    // Parse a year; non-numeric or out of range becomes 0 (= unset).
    private static int parseYear(String text) {
        try {
            int y = Integer.parseInt(text.trim());
            return (y > 0 && y < 3000) ? y : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
