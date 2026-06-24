// Author: Ruiqi Huang
// Description: merge detections from overlapping tiles, dropping near-duplicates
// (the same book seen at a tile seam). Pure geometry.
package shelfscape.scan;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import shelfscape.model.Detection;

public final class DetectionMerge {

    private DetectionMerge() {
        // Ignore this
    }

    // Two boxes count as the same book if they overlap by at least this fraction
    // of the smaller box.
    private static final double DUP_OVERLAP = 0.5;

    // Keep one detection per physical book. Bigger boxes win (usually the cleaner
    // read), so we consider them first.
    public static List<Detection> dedupe(List<Detection> detections) {
        List<Detection> sorted = new ArrayList<>(detections);
        sorted.sort(Comparator.comparingInt(DetectionMerge::area).reversed());

        List<Detection> kept = new ArrayList<>();
        for (Detection d : sorted) {
            Rectangle b = d.getBbox();
            if (b == null) {
                kept.add(d);
                continue;
            }
            boolean duplicate = false;
            for (Detection k : kept) {
                Rectangle kb = k.getBbox();
                if (kb != null && overlapOfSmaller(b, kb) >= DUP_OVERLAP) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) {
                kept.add(d);
            }
        }
        return kept;
    }

    private static int area(Detection d) {
        Rectangle b = d.getBbox();
        return b == null ? 0 : b.width * b.height;
    }

    // Intersection area as a fraction of the smaller box's area.
    private static double overlapOfSmaller(Rectangle a, Rectangle b) {
        int x = Math.max(a.x, b.x);
        int y = Math.max(a.y, b.y);
        int x2 = Math.min(a.x + a.width, b.x + b.width);
        int y2 = Math.min(a.y + a.height, b.y + b.height);
        int inter = Math.max(0, x2 - x) * Math.max(0, y2 - y);
        int min = Math.min(a.width * a.height, b.width * b.height);
        return min <= 0 ? 0 : (double) inter / min;
    }
}
