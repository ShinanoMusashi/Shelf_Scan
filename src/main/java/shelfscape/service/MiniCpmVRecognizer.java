// Author: Ruiqi Huang
// Description: the real recogniser, this part talks to the OCR directly
package shelfscape.service;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import shelfscape.model.Detection;
import shelfscape.util.Json;

//  IMPORTANT! THIRD-PARTY MODEL
//  The actual book recognition is done by MiniCPM-V, an EXTERNAL third-party
//  vision-language model by OpenBMB, run locally through Ollama, which are NOT my
//  work. This class is my work: 1) encodes the photo + builds the
//  HTTP request, 2) POSTs it to the local Ollama server, 3) parses the JSON
//  reply into Detection objects. I did not write/train/modify the model.

public class MiniCpmVRecognizer implements VlmRecognizer {

    // Ollama's "generate" endpoint on the local machine.
    public static final String DEFAULT_ENDPOINT = "http://localhost:11434/api/generate";

    // The model tag pulled into Ollama.
    public static final String DEFAULT_MODEL = "minicpm-v:latest";

    // Shrink the photo so neither side exceeds this before sending keeps the
    // model fast. 1024 px is a good speed which I found
    private static final int MAX_SIDE = 1024;

    // Read timeout
    private static final int TIMEOUT_MS = 300_000;

    // The prompt. Asking for one JSON entry per spine (with a box) keeps parsing
    // simple and gives ShelfPanel something to draw.
    private static final String PROMPT =
            "You are looking at a photograph of a bookshelf.\n\n"
            + "List EVERY book you can read on the shelves. Return EXACTLY ONE entry per\n"
            + "physical book spine — never split a book's title and author into two entries.\n"
            + "For each book report:\n"
            + "  - title:   the title exactly as printed on the spine\n"
            + "  - author:  the author exactly as printed (empty string if not visible)\n"
            + "  - bbox_2d: the bounding box of that book's whole spine, as pixel coordinates\n"
            + "             [x1, y1, x2, y2] in THIS image (top-left origin).\n\n"
            + "Do NOT invent titles or authors — only report what you can actually read.\n\n"
            + "Return ONLY a JSON array, no prose, no markdown fences. Each item:\n"
            + "{\"title\": \"<title>\", \"author\": \"<author or empty>\", \"bbox_2d\": [x1, y1, x2, y2]}\n";

    private final String endpoint;
    private final String model;

    public MiniCpmVRecognizer() {
        this(DEFAULT_ENDPOINT, DEFAULT_MODEL);
    }

    public MiniCpmVRecognizer(String endpoint, String model) {
        this.endpoint = endpoint;
        this.model = model;
    }

    // Encode the image, POST it to Ollama, and parse the reply into Detections.
    // Boxes come back in the original photo's pixel space.
    @Override
    public List<Detection> detect(BufferedImage image) throws IOException {
        Prepared prep = prepareImage(image);
        String body = buildRequestBody(prep.base64);
        String raw = postToOllama(body);
        return parseResponse(raw, prep.sentWidth, prep.sentHeight,
                image.getWidth(), image.getHeight());
    }


    // The base64 JPEG we send, plus the pixel size it was sent
    private static final class Prepared {
        final String base64;
        final int sentWidth;
        final int sentHeight;

        Prepared(String base64, int sentWidth, int sentHeight) {
            this.base64 = base64;
            this.sentWidth = sentWidth;
            this.sentHeight = sentHeight;
        }
    }

    private Prepared prepareImage(BufferedImage src) throws IOException {
        int w = src.getWidth();
        int h = src.getHeight();
        double scale = Math.max(w, h) > MAX_SIDE ? (double) MAX_SIDE / Math.max(w, h) : 1.0;
        int sw = Math.max(1, (int) Math.round(w * scale));
        int sh = Math.max(1, (int) Math.round(h * scale));

        // JPEG has no alpha channel, so always render onto an RGB canvas.
        BufferedImage rgb = new BufferedImage(sw, sh, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, sw, sh, null);
        g.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(rgb, "jpg", baos);
        String b64 = Base64.getEncoder().encodeToString(baos.toByteArray());
        return new Prepared(b64, sw, sh);
    }


    // Build the Ollama JSON request body around the encoded image.
    private String buildRequestBody(String base64Image) {
        Map<String, Object> payload = Json.object();
        payload.put("model", model);
        payload.put("prompt", PROMPT);
        List<Object> images = Json.array();
        images.add(base64Image);
        payload.put("images", images);
        payload.put("stream", Boolean.FALSE);
        // Keep the model resident for 30 min so a follow up scan doesn't pay the cold reload.
        payload.put("keep_alive", "30m");

        Map<String, Object> options = Json.object();
        options.put("temperature", 0.1);
        // near-deterministic; we want literal reads
        options.put("num_ctx", 8192L);
        // cap context so Ollama doesn't over-allocate
        payload.put("options", options);

        return Json.stringify(payload);
    }

    // Send the request and return the model's text reply.
    private String postToOllama(String body) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) URI.create(endpoint).toURL().openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setConnectTimeout(5_000);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setDoOutput(true);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }

            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                String err = readAll(conn.getErrorStream());
                throw new IOException("Ollama returned HTTP " + code
                        + (err.isEmpty() ? "" : ": " + err));
            }

            String responseJson = readAll(conn.getInputStream());
            // Ollama wraps the model's text
            Map<String, Object> obj = Json.parseObject(responseJson);
            Object text = obj.get("response");
            return text == null ? "" : text.toString();
        } finally {
            conn.disconnect();
        }
    }

    // Read a whole stream into a UTF-8 string.
    private static String readAll(InputStream in) throws IOException {
        if (in == null) {
            return "";
        }
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int n;
        try (InputStream stream = in) {
            while ((n = stream.read(chunk)) != -1) {
                buf.write(chunk, 0, n);
            }
        }
        return new String(buf.toByteArray(), StandardCharsets.UTF_8);
    }

    // Pull the JSON array out of the model's text and turn each item into a
    // Detection (boxes in the original photo's pixels). The model sometimes wraps
    // the array in markdown/prose, so we grab the outermost first.
    // sentW/sentH = size we sent; origW/origH = original photo size.
    List<Detection> parseResponse(String raw, int sentW, int sentH, int origW, int origH) {
        List<Detection> detections = new ArrayList<>();
        String arrayText = extractJsonArray(raw);
        if (arrayText == null) {
            return detections;
        }

        Object parsed;
        try {
            parsed = Json.parse(arrayText);
        } catch (Json.JsonException e) {
            return detections; // garbled JSON → no detections (handled gracefully)
        }
        if (!(parsed instanceof List)) {
            return detections;
        }

        for (Object element : (List<?>) parsed) {
            if (!(element instanceof Map)) {
                continue;
            }
            Map<?, ?> item = (Map<?, ?>) element;
            String title = asString(item.get("title"));
            if (title.isEmpty()) {
                continue; // never invent a book with no title
            }
            Rectangle bbox = parseBbox(item.get("bbox_2d"), sentW, sentH, origW, origH);
            detections.add(new Detection(title, bbox));
        }
        return detections;
    }

    // Return the outermost substring of raw, or null if there isn't one.
    private static String extractJsonArray(String raw) {
        if (raw == null) {
            return null;
        }
        int start = raw.indexOf('[');
        int end = raw.lastIndexOf(']');
        if (start < 0 || end <= start) {
            return null;
        }
        return raw.substring(start, end + 1);
    }

    // Convert a [x1,y1,x2,y2] box into original-photo pixels. MiniCPM-V is
    // inconsistent — sometimes pixels in the sent image, sometimes 0-1 fractions
    // (occasionally a bit over 1) — so we detect which and convert accordingly.
    private static Rectangle parseBbox(Object value, int sentW, int sentH, int origW, int origH) {
        if (!(value instanceof List)) {
            return null;
        }
        List<?> nums = (List<?>) value;
        if (nums.size() != 4) {
            return null;
        }
        double x1 = asDouble(nums.get(0));
        double y1 = asDouble(nums.get(1));
        double x2 = asDouble(nums.get(2));
        double y2 = asDouble(nums.get(3));

        // If every value is ≤ ~1.5, the model gave normalised fractions; multiply
        // straight into the original photo. Otherwise they are sent-image pixels,
        // so scale by (orig / sent).
        double maxAbs = Math.max(Math.max(Math.abs(x1), Math.abs(y1)),
                Math.max(Math.abs(x2), Math.abs(y2)));
        boolean normalized = maxAbs <= 1.5;
        double fx = normalized ? origW : (double) origW / sentW;
        double fy = normalized ? origH : (double) origH / sentH;

        int left = (int) Math.round(clamp(Math.min(x1, x2) * fx, 0, origW));
        int right = (int) Math.round(clamp(Math.max(x1, x2) * fx, 0, origW));
        int top = (int) Math.round(clamp(Math.min(y1, y2) * fy, 0, origH));
        int bottom = (int) Math.round(clamp(Math.max(y1, y2) * fy, 0, origH));
        if (right - left <= 0 || bottom - top <= 0) {
            return null; // degenerate box → treat as "no location"
        }
        return new Rectangle(left, top, right - left, bottom - top);
    }

    // Keep v within [lo, hi].
    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    // In case of null
    private static String asString(Object o) {
        return o == null ? "" : o.toString().trim();
    }

    //Number read
    private static double asDouble(Object o) {
        if (o instanceof Number) {
            return ((Number) o).doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(o));
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}
