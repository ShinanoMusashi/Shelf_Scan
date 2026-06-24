// Author: Ruiqi Huang
// Description: a book on a shelf
package shelfscape.model;

import java.awt.Rectangle;

import shelfscape.util.TextLine;

public class Book {

    // The fields
    private String title;
    private String author;
    private String isbn;
    private int year;
    private String description;
    private Rectangle bbox;
    private ReadingStatus status = ReadingStatus.NONE;

    // Empty book, this is use when rebuilding from a saved line.
    public Book() {
    }

    // A freshly detected book: title and position, rest filled in later by the user
    public Book(String title, Rectangle bbox) {
        this.title = title;
        this.bbox = bbox;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getIsbn() {
        return isbn;
    }

    public void setIsbn(String isbn) {
        this.isbn = isbn;
    }

    public int getYear() {
        return year;
    }

    public void setYear(int year) {
        this.year = year;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Rectangle getBbox() {
        return bbox;
    }

    public void setBbox(Rectangle bbox) {
        this.bbox = bbox;
    }

    public ReadingStatus getStatus() {
        return status;
    }

    public void setStatus(ReadingStatus status) {
        this.status = status;
    }

    // Field order: title, author, isbn, year, status, bx, by, bw, bh, notes.
    // This is for saving the stuff, you told me to use toString and it's actually way easier
    @Override
    public String toString() {
        // Box fields stay empty when there's no box.
        String bx = "", by = "", bw = "", bh = "";
        if (bbox != null) {
            bx = Integer.toString(bbox.x);
            by = Integer.toString(bbox.y);
            bw = Integer.toString(bbox.width);
            bh = Integer.toString(bbox.height);
        }
        // Escape text fields so a newline in them can't break the line (remove the spaces or returns)
        return String.join("\t",
                TextLine.escape(title),
                TextLine.escape(author),
                TextLine.escape(isbn),
                Integer.toString(year),
                (status == null ? ReadingStatus.NONE : status).name(),
                bx, by, bw, bh,
                TextLine.escape(description));
    }

    // Rebuild a Book from a toString() line
    public static Book fromString(String line) {
        if (line == null) {
            return null;
        }
        // -1 keeps trailing empty fields (for example an empty notes column).
        String[] f = line.split("\t", -1);
        if (f.length < 10) {
            return null;
        }
        Book b = new Book();
        b.setTitle(TextLine.unescape(f[0]));
        b.setAuthor(TextLine.unescape(f[1]));
        b.setIsbn(TextLine.unescape(f[2]));
        b.setYear(parseIntSafe(f[3]));
        b.setStatus(parseStatus(f[4]));
        // Empty x-field means the book had no box
        if (!f[5].isEmpty()) {
            b.setBbox(new Rectangle(parseIntSafe(f[5]), parseIntSafe(f[6]),
                    parseIntSafe(f[7]), parseIntSafe(f[8])));
        }
        b.setDescription(TextLine.unescape(f[9]));
        return b;
    }

    // Parse an int, treating junk as 0
    private static int parseIntSafe(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // Parse a status name
    private static ReadingStatus parseStatus(String s) {
        try {
            return ReadingStatus.valueOf(s.trim());
        } catch (IllegalArgumentException e) {
            return ReadingStatus.NONE;
        }
    }
}
