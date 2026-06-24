// Author: Ruiqi Huang
// Description: rotate a photo by 0/90/180/270°
package shelfscape.util;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;

public final class ImageRotation {

    private ImageRotation() {
        // Ignore this
    }

    // Rotate src clockwise by degCw (0/90/180/270). Returns src unchanged for 0.
    public static BufferedImage rotate(BufferedImage src, int degCw) {
        int deg = normalize(degCw);
        if (deg == 0) {
            return src;
        }
        int w = src.getWidth();
        int h = src.getHeight();
        // 90/270 swap width and height; 180 keeps them.
        int nw = (deg == 180) ? w : h;
        int nh = (deg == 180) ? h : w;

        BufferedImage dst = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = dst.createGraphics();
        // Translate first so the rotated image lands inside the new canvas.
        switch (deg) {
            case 90:
                g.translate(h, 0);
                g.rotate(Math.toRadians(90));
                break;
            case 180:
                g.translate(w, h);
                g.rotate(Math.toRadians(180));
                break;
            case 270:
                g.translate(0, w);
                g.rotate(Math.toRadians(270));
                break;
            default:
                break;
        }
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return dst;
    }

    // Map a box from the rotated image back to the original photo's pixels.
    // degCw is the rotation that was applied; origW/origH are the original size.
    public static Rectangle unrotateRect(Rectangle r, int degCw, int origW, int origH) {
        int deg = normalize(degCw);
        if (r == null) {
            return null;
        }
        if (deg == 0) {
            return new Rectangle(r);
        }
        // Map the box's two corners back, then take the bounding box of the result.
        int[] xs = { r.x, r.x + r.width };
        int[] ys = { r.y, r.y + r.height };
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        for (int xr : xs) {
            for (int yr : ys) {
                int[] o = mapPointBack(xr, yr, deg, origW, origH);
                minX = Math.min(minX, o[0]);
                minY = Math.min(minY, o[1]);
                maxX = Math.max(maxX, o[0]);
                maxY = Math.max(maxY, o[1]);
            }
        }
        // Keep it inside the original image.
        minX = Math.max(0, minX);
        minY = Math.max(0, minY);
        maxX = Math.min(origW, maxX);
        maxY = Math.min(origH, maxY);
        return new Rectangle(minX, minY, Math.max(0, maxX - minX), Math.max(0, maxY - minY));
    }

    // Inverse of rotate(), for a single point (rotated coords -> original coords).
    private static int[] mapPointBack(int xr, int yr, int deg, int origW, int origH) {
        switch (deg) {
            case 90:
                return new int[] { yr, origH - xr };
            case 180:
                return new int[] { origW - xr, origH - yr };
            case 270:
                return new int[] { origW - yr, xr };
            default:
                return new int[] { xr, yr };
        }
    }

    // Force the angle into {0,90,180,270}; reject anything else.
    private static int normalize(int deg) {
        int d = ((deg % 360) + 360) % 360;
        if (d != 0 && d != 90 && d != 180 && d != 270) {
            throw new IllegalArgumentException("Rotation must be 0/90/180/270, got " + deg);
        }
        return d;
    }
}
