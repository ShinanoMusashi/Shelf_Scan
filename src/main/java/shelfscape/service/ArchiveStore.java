// Author: Ruiqi Huang
// Description: save/load the whole archive as a simple text file (no JSON). It should look like this:
//   SHELF <tab> id <tab> name <tab> scanDate <tab> sourceImagePath
//   BOOK  <tab> <Book.toString()>
package shelfscape.service;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import shelfscape.model.Archive;
import shelfscape.model.Book;
import shelfscape.model.Shelf;
import shelfscape.util.TextLine;

public final class ArchiveStore {

    private ArchiveStore() {
        // Ignore this
    }

    // Write the archive out in the text format above (in the description)
    public static void save(Archive archive, File file) throws IOException {
        try (BufferedWriter w = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
            for (Shelf shelf : archive.getShelves().values()) {
                // One SHELF header line ->
                w.write("SHELF\t"
                        + TextLine.escape(shelf.getId()) + "\t"
                        + TextLine.escape(shelf.getName()) + "\t"
                        + TextLine.escape(shelf.getScanDate()) + "\t"
                        + TextLine.escape(shelf.getSourceImagePath()));
                w.newLine();
                // -> then one BOOK line per book under it.
                for (Book book : shelf.getBooks()) {
                    w.write("BOOK\t" + book.toString());
                    w.newLine();
                }
            }
        }
    }

    // Read the archive back; returns an empty one if the file doesn't exist (it does not exist)
    public static Archive load(File file) throws IOException {
        Archive archive = new Archive();
        if (file == null || !file.exists()) {
            return archive;
        }
        try (BufferedReader r = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            String line;
            // books attach to the latest shelf seen
            Shelf current = null;
            while ((line = r.readLine()) != null) {
                int tab = line.indexOf('\t');
                if (tab < 0) {
                    continue;
                    // not a record line
                }
                String tag = line.substring(0, tab);
                // "SHELF" or "BOOK"
                String rest = line.substring(tab + 1);
                // the fields after it
                if ("SHELF".equals(tag)) {
                    current = parseShelf(rest);
                    archive.addShelf(current);
                } else if ("BOOK".equals(tag) && current != null) {
                    Book book = Book.fromString(rest);
                    if (book != null) {
                        // skip a corrupt book line
                        current.addBook(book);
                    }
                }
            }
        }
        return archive;
    }

    // Rebuild a Shelf from a SHELF line's fields
    private static Shelf parseShelf(String rest) {
        String[] f = rest.split("\t", -1);
        Shelf shelf = new Shelf(
                f.length > 0 ? TextLine.unescape(f[0]) : "",
                f.length > 1 ? TextLine.unescape(f[1]) : "");
        if (f.length > 2) {
            shelf.setScanDate(TextLine.unescape(f[2]));
        }
        if (f.length > 3) {
            shelf.setSourceImagePath(TextLine.unescape(f[3]));
        }
        return shelf;
    }
}
