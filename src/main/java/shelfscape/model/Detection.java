// Author: Ruiqi Huang
// Description: one raw result from the OCR model (title + box), before it becomes a Book
package shelfscape.model;

import java.awt.Rectangle;

public class Detection {

    // What the model reads
    private final String rawTitle;
    // Where is the book?
    private Rectangle bbox;            // where it saw the book (may be replaced/cleaned)

    public Detection(String rawTitle, Rectangle bbox) {
        this.rawTitle = rawTitle;
        this.bbox = bbox;
    }

    public String getRawTitle() {
        return rawTitle;
    }

    public Rectangle getBbox() {
        return bbox;
    }

    // The controller rewrites the box
    public void setBbox(Rectangle bbox) {
        this.bbox = bbox;
    }
}
