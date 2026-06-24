// Author: Ruiqi Huang
// Description: A Json file reader/writer
package shelfscape.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Json {

    private Json() {
        // Ignore this
    }

    // Thrown when the input isn't valid JSON.
    public static final class JsonException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public JsonException(String message) {
            super(message);
        }
    }

    // A new, empty JSON object (insertion order is kept).
    public static Map<String, Object> object() {
        return new LinkedHashMap<>();
    }

    // A new, empty JSON array.
    public static List<Object> array() {
        return new ArrayList<>();
    }

    // Parse any JSON value; throws JsonException if there's leftover text.
    public static Object parse(String text) {
        Parser p = new Parser(text);
        p.skipWhitespace();
        Object value = p.parseValue();
        p.skipWhitespace();
        if (!p.atEnd()) {
            throw new JsonException("Unexpected trailing characters at index " + p.pos);
        }
        return value;
    }

    // Parse text and require the top-level value to be a JSON object.
    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String text) {
        Object value = parse(text);
        if (!(value instanceof Map)) {
            throw new JsonException("Expected a JSON object at the top level");
        }
        return (Map<String, Object>) value;
    }

    private static final class Parser {
        private final String src;
        private int pos;

        Parser(String src) {
            this.src = src;
        }

        boolean atEnd() {
            return pos >= src.length();
        }

        void skipWhitespace() {
            while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) {
                pos++;
            }
        }

        Object parseValue() {
            skipWhitespace();
            if (atEnd()) {
                throw new JsonException("Unexpected end of input");
            }
            // Dispatch on the first character of the value.
            char c = src.charAt(pos);
            switch (c) {
                case '{':
                    return parseObjectBody();
                case '[':
                    return parseArrayBody();
                case '"':
                    return parseString();
                case 't':
                case 'f':
                    return parseBoolean();
                case 'n':
                    return parseNull();
                default:
                    return parseNumber();
            }
        }

        private Map<String, Object> parseObjectBody() {
            Map<String, Object> map = new LinkedHashMap<>();
            expect('{');
            skipWhitespace();
            if (peek() == '}') {
                pos++;
                return map;
            }
            while (true) {
                skipWhitespace();
                String key = parseString();
                skipWhitespace();
                expect(':');
                Object value = parseValue();
                map.put(key, value);
                skipWhitespace();
                char c = next();
                if (c == '}') {
                    return map;
                }
                if (c != ',') {
                    throw new JsonException("Expected ',' or '}' in object at index " + (pos - 1));
                }
            }
        }

        private List<Object> parseArrayBody() {
            List<Object> list = new ArrayList<>();
            expect('[');
            skipWhitespace();
            if (peek() == ']') {
                pos++;
                return list;
            }
            while (true) {
                list.add(parseValue());
                skipWhitespace();
                char c = next();
                if (c == ']') {
                    return list;
                }
                if (c != ',') {
                    throw new JsonException("Expected ',' or ']' in array at index " + (pos - 1));
                }
            }
        }

        private String parseString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                if (atEnd()) {
                    throw new JsonException("Unterminated string");
                }
                char c = src.charAt(pos++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c == '\\') {
                    char esc = next();
                    switch (esc) {
                        case '"':  sb.append('"');  break;
                        case '\\': sb.append('\\'); break;
                        case '/':  sb.append('/');  break;
                        case 'b':  sb.append('\b'); break;
                        case 'f':  sb.append('\f'); break;
                        case 'n':  sb.append('\n'); break;
                        case 'r':  sb.append('\r'); break;
                        case 't':  sb.append('\t'); break;
                        case 'u':
                            if (pos + 4 > src.length()) {
                                throw new JsonException("Incomplete \\u escape");
                            }
                            sb.append((char) Integer.parseInt(src.substring(pos, pos + 4), 16));
                            pos += 4;
                            break;
                        default:
                            throw new JsonException("Invalid escape '\\" + esc + "'");
                    }
                } else {
                    sb.append(c);
                }
            }
        }

        private Object parseNumber() {
            int start = pos;
            boolean floating = false;
            if (peek() == '-') {
                pos++;
            }
            while (!atEnd()) {
                char c = src.charAt(pos);
                if (c >= '0' && c <= '9') {
                    pos++;
                } else if (c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') {
                    floating = true;
                    pos++;
                } else {
                    break;
                }
            }
            String num = src.substring(start, pos);
            if (num.isEmpty() || "-".equals(num)) {
                throw new JsonException("Invalid number at index " + start);
            }
            try {
                return floating ? (Object) Double.valueOf(num) : (Object) Long.valueOf(num);
            } catch (NumberFormatException e) {
                // e.g. an integer too large for long — fall back to double
                return Double.valueOf(num);
            }
        }

        private Boolean parseBoolean() {
            if (src.startsWith("true", pos)) {
                pos += 4;
                return Boolean.TRUE;
            }
            if (src.startsWith("false", pos)) {
                pos += 5;
                return Boolean.FALSE;
            }
            throw new JsonException("Invalid literal at index " + pos);
        }

        private Object parseNull() {
            if (src.startsWith("null", pos)) {
                pos += 4;
                return null;
            }
            throw new JsonException("Invalid literal at index " + pos);
        }

        private char peek() {
            if (atEnd()) {
                throw new JsonException("Unexpected end of input");
            }
            return src.charAt(pos);
        }

        private char next() {
            if (atEnd()) {
                throw new JsonException("Unexpected end of input");
            }
            return src.charAt(pos++);
        }

        private void expect(char c) {
            char actual = next();
            if (actual != c) {
                throw new JsonException("Expected '" + c + "' but found '" + actual + "' at index " + (pos - 1));
            }
        }
    }
    // Serialize a value tree to compact JSON.
    public static String stringify(Object value) {
        StringBuilder sb = new StringBuilder();
        write(value, sb, false, 0);
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static void write(Object value, StringBuilder sb, boolean pretty, int depth) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof Map) {
            writeObject((Map<String, Object>) value, sb, pretty, depth);
        } else if (value instanceof List) {
            writeArray((List<Object>) value, sb, pretty, depth);
        } else if (value instanceof String) {
            writeString((String) value, sb);
        } else if (value instanceof Boolean || value instanceof Number) {
            sb.append(value.toString());
        } else {
            // Unknown type: emit its toString as a JSON string so output stays valid.
            writeString(value.toString(), sb);
        }
    }

    private static void writeObject(Map<String, Object> map, StringBuilder sb, boolean pretty, int depth) {
        if (map.isEmpty()) {
            sb.append("{}");
            return;
        }
        sb.append('{');
        int i = 0;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (i++ > 0) {
                sb.append(',');
            }
            newlineIndent(sb, pretty, depth + 1);
            writeString(entry.getKey(), sb);
            sb.append(pretty ? ": " : ":");
            write(entry.getValue(), sb, pretty, depth + 1);
        }
        newlineIndent(sb, pretty, depth);
        sb.append('}');
    }

    private static void writeArray(List<Object> list, StringBuilder sb, boolean pretty, int depth) {
        if (list.isEmpty()) {
            sb.append("[]");
            return;
        }
        sb.append('[');
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            newlineIndent(sb, pretty, depth + 1);
            write(list.get(i), sb, pretty, depth + 1);
        }
        newlineIndent(sb, pretty, depth);
        sb.append(']');
    }

    private static void writeString(String s, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b");  break;
                case '\f': sb.append("\\f");  break;
                case '\n': sb.append("\\n");  break;
                case '\r': sb.append("\\r");  break;
                case '\t': sb.append("\\t");  break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
    }

    private static void newlineIndent(StringBuilder sb, boolean pretty, int depth) {
        if (pretty) {
            sb.append('\n');
            for (int i = 0; i < depth; i++) {
                sb.append("  ");
            }
        }
    }
}
