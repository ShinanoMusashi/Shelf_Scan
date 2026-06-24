// Author: Ruiqi Huang
// Description: group raw detections into shelf ROWS by clustering their vertical
// position. Pure geometry (no UI, no model) so it can be reused on any platform.
package shelfscape.scan;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import shelfscape.model.Detection;

public final class RowDetector {

    private RowDetector() {
        // Ignore this
    }

    // Two boxes belong to the same row if their vertical extents overlap by at
    // least this fraction of the smaller box's height.
    private static final double SAME_ROW_OVERLAP = 0.35;

    // Cluster detections into rows. Returns rows top-to-bottom; each row's books
    // are ordered left-to-right. Detections without a box are ignored.
    public static List<List<Detection>> detectRows(List<Detection> detections) {
        List<Detection> boxed = new ArrayList<>();
        for (Detection d : detections) {
            if (d.getBbox() != null) {
                boxed.add(d);
            }
        }
        // Process top-to-bottom so growing bands stay coherent.
        boxed.sort(Comparator.comparingInt(d -> d.getBbox().y + d.getBbox().height / 2));

        List<int[]> bands = new ArrayList<>();          // {top, bottom} per row
        List<List<Detection>> rows = new ArrayList<>();
        for (Detection d : boxed) {
            Rectangle b = d.getBbox();
            int idx = -1;
            for (int i = 0; i < bands.size(); i++) {
                if (verticalOverlap(b, bands.get(i)) >= SAME_ROW_OVERLAP) {
                    idx = i;
                    break;
                }
            }
            if (idx < 0) {
                bands.add(new int[] { b.y, b.y + b.height });
                rows.add(new ArrayList<>());
                idx = rows.size() - 1;
            } else {
                bands.get(idx)[0] = Math.min(bands.get(idx)[0], b.y);
                bands.get(idx)[1] = Math.max(bands.get(idx)[1], b.y + b.height);
            }
            rows.get(idx).add(d);
        }

        // Order rows top-to-bottom by band top, and each row left-to-right.
        Integer[] order = new Integer[rows.size()];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }
        Arrays.sort(order, Comparator.comparingInt(i -> bands.get(i)[0]));

        List<List<Detection>> out = new ArrayList<>();
        for (int i : order) {
            List<Detection> row = rows.get(i);
            row.sort(Comparator.comparingInt(d -> d.getBbox().x + d.getBbox().width / 2));
            out.add(row);
        }
        return out;
    }

    // Fraction of the smaller height where box b and the band [top,bottom] overlap.
    private static double verticalOverlap(Rectangle b, int[] band) {
        int top = Math.max(b.y, band[0]);
        int bottom = Math.min(b.y + b.height, band[1]);
        int inter = Math.max(0, bottom - top);
        int minH = Math.min(b.height, band[1] - band[0]);
        return minH <= 0 ? 0 : (double) inter / minH;
    }
}
