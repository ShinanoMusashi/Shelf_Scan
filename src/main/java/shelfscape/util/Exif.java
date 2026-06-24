// Author: Ruiqi Huang
// Description: read a JPEG's EXIF "Orientation" tag and rotate/flip the image upright.
package shelfscape.util;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

public final class Exif {

    private Exif() {
        // Ignore this
    }

    private static final int TAG_ORIENTATION = 0x0112;   // EXIF orientation tag id

    // Read the orientation (1-8) from a JPEG; 1 if absent or anything goes wrong.
    public static int readOrientation(File file) {
        try (InputStream in = new BufferedInputStream(new FileInputStream(file))) {
            return parseJpeg(in);
        } catch (Exception e) {
            return 1; // any trouble; assume normal orientation
        }
    }

    // Return a copy of img rotated/flipped to match the EXIF orientation value.
    public static BufferedImage applyOrientation(BufferedImage img, int orientation) {
        switch (orientation) {
            case 2:  return flipHorizontal(img);
            case 3:  return ImageRotation.rotate(img, 180);
            case 4:  return flipVertical(img);
            case 5:  return flipHorizontal(ImageRotation.rotate(img, 90));
            case 6:  return ImageRotation.rotate(img, 90);
            case 7:  return flipHorizontal(ImageRotation.rotate(img, 270));
            case 8:  return ImageRotation.rotate(img, 270);
            default: return img; // 1, or anything unexpected
        }
    }

    // Walk the JPEG markers until we hit the EXIF (APP1) segment or run out.
    private static int parseJpeg(InputStream in) throws IOException {
        if (read16be(in) != 0xFFD8) {
            return 1; // not a JPEG (no Start-Of-Image marker)
        }
        while (true) {
            int marker = in.read();
            if (marker == -1) {
                return 1;
            }
            if (marker != 0xFF) {
                continue; // resync to the next marker byte
            }
            int type;
            do {
                type = in.read();
            } while (type == 0xFF);
            // skip fill bytes
            if (type == -1 || type == 0xDA || type == 0xD9) {
                return 1;
                // start-of-scan / end-of-image: no metadata left
            }
            int length = read16be(in);
            // segment length includes these 2 bytes
            if (length < 2) {
                return 1;
            }
            byte[] segment = readFully(in, length - 2);
            if (type == 0xE1 && isExif(segment)) {
                // 0xE1 = APP1
                return parseExif(segment);
            }
        }
    }

    // APP1 EXIF segments start with the ASCII "Exif\0\0".
    private static boolean isExif(byte[] seg) {
        return seg.length >= 6
                && seg[0] == 'E' && seg[1] == 'x' && seg[2] == 'i' && seg[3] == 'f'
                && seg[4] == 0 && seg[5] == 0;
    }

    // Read the little TIFF block inside the EXIF segment and find the orientation.
    private static int parseExif(byte[] seg) {
        int tiff = 6; // skip "Exif\0\0"
        if (seg.length < tiff + 8) {
            return 1;
        }
        // Byte order: "II" = little-endian, "MM" = big-endian.
        boolean little;
        if (seg[tiff] == 'I' && seg[tiff + 1] == 'I') {
            little = true;
        } else if (seg[tiff] == 'M' && seg[tiff + 1] == 'M') {
            little = false;
        } else {
            return 1;
        }

        int ifdOffset = readInt(seg, tiff + 4, little);
        // offset to the first IFD
        int ifd = tiff + ifdOffset;
        if (ifd < 0 || ifd + 2 > seg.length) {
            return 1;
        }
        int entries = readShort(seg, ifd, little);
        // number of tags
        int p = ifd + 2;
        for (int i = 0; i < entries; i++, p += 12) {
            // each entry is 12 bytes
            if (p + 12 > seg.length) {
                break;
            }
            int tag = readShort(seg, p, little);
            if (tag == TAG_ORIENTATION) {
                int value = readShort(seg, p + 8, little);
                // SHORT value sits first
                return (value >= 1 && value <= 8) ? value : 1;
            }
        }
        return 1;
    }
    // Read a big-endian 16-bit value (used for JPEG markers/lengths).
    private static int read16be(InputStream in) throws IOException {
        int a = in.read();
        int b = in.read();
        if (a == -1 || b == -1) {
            return -1;
        }
        return (a << 8) | b;
    }

    // Read exactly n bytes, or fail.
    private static byte[] readFully(InputStream in, int n) throws IOException {
        byte[] buf = new byte[n];
        int off = 0;
        while (off < n) {
            int r = in.read(buf, off, n - off);
            if (r == -1) {
                throw new IOException("Unexpected end of JPEG segment");
            }
            off += r;
        }
        return buf;
    }

    // 16-bit value from the byte array, honouring the TIFF byte order.
    private static int readShort(byte[] b, int off, boolean little) {
        int b0 = b[off] & 0xFF;
        int b1 = b[off + 1] & 0xFF;
        return little ? (b1 << 8) | b0 : (b0 << 8) | b1;
    }

    // 32-bit value from the byte array, honouring the TIFF byte order.
    private static int readInt(byte[] b, int off, boolean little) {
        int b0 = b[off] & 0xFF;
        int b1 = b[off + 1] & 0xFF;
        int b2 = b[off + 2] & 0xFF;
        int b3 = b[off + 3] & 0xFF;
        return little ? (b3 << 24) | (b2 << 16) | (b1 << 8) | b0
                      : (b0 << 24) | (b1 << 16) | (b2 << 8) | b3;
    }
    // Mirror left<->right: draw with the source x-range reversed.
    private static BufferedImage flipHorizontal(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = dst.createGraphics();
        g.drawImage(img, 0, 0, w, h, w, 0, 0, h, null);
        g.dispose();
        return dst;
    }

    // Mirror top<->bottom: draw with the source y-range reversed.
    private static BufferedImage flipVertical(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = dst.createGraphics();
        g.drawImage(img, 0, 0, w, h, 0, h, w, 0, null);
        g.dispose();
        return dst;
    }
}
