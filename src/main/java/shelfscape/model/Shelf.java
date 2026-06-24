// Author: Ruiqi Huang
// Model: one scanned shelf with its photo and the books found on it
package shelfscape.model;

import java.util.ArrayList;
import java.util.List;

public class Shelf {

    // Fields
    private String id;
    private String name;
    private String scanDate;
    private String sourceImagePath;

    // ArrayList: order matters (left-to-right) and we mostly iterate
    private final List<Book> books = new ArrayList<>();

    // Empty shelf, this is used when rebuilding from the save file.
    public Shelf() {
    }

    public Shelf(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getScanDate() {
        return scanDate;
    }

    public void setScanDate(String scanDate) {
        this.scanDate = scanDate;
    }

    public String getSourceImagePath() {
        return sourceImagePath;
    }

    public void setSourceImagePath(String sourceImagePath) {
        this.sourceImagePath = sourceImagePath;
    }

    public List<Book> getBooks() {
        return books;
    }

    public void addBook(Book book) {
        books.add(book);
    }
}
