// Author: Ruiqi Huang
// Description: holds the info and id of the books
package shelfscape.model;

import java.util.HashMap;
import java.util.Map;

public class Archive {

    // HashMap: the main operation is fetching a shelf by id in O(1)
    private final Map<String, Shelf> shelves = new HashMap<>();

    // Add a shelf by its id
    public void addShelf(Shelf shelf) {
        shelves.put(shelf.getId(), shelf);
    }

    public Map<String, Shelf> getShelves() {
        return shelves;
    }

    public int shelfCount() {
        return shelves.size();
    }

    // Total books for all shelves
    public int bookCount() {
        int total = 0;
        for (Shelf shelf : shelves.values()) {
            total += shelf.getBooks().size();
        }
        return total;
    }
}
