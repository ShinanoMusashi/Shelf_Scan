// Author: Ruiqi Huang
// Description: the "find books in a photo" contract.
package shelfscape.service;

import java.awt.image.BufferedImage;
import java.util.List;

import shelfscape.model.Detection;

public interface VlmRecognizer {

    // Find every book in the image. Return an empty list.
    List<Detection> detect(BufferedImage image) throws Exception;
}
