// Author: Ruiqi Huang
// Description: utility for encoding/decoding text lines in the .shelfscape format. We use tabs to separate fields,
// and newlines to separate lines, so we need to escape those characters if they appear in the text.
// We also drop carriage returns since they don't have a standard representation across platforms.
package shelfscape.util;

public final class TextLine {

    private TextLine() {
        // Ignore this
    }

    // Encode a field so it has no raw tab/newline/CR (which would break the line).
    public static String escape(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\': b.append("\\\\"); break;  // backslash -> \\
                case '\t': b.append("\\t");  break;  // tab       -> \t
                case '\n': b.append("\\n");  break;  // newline   -> \n
                case '\r': break;                    // drop CR entirely
                default:   b.append(c);
            }
        }
        return b.toString();
    }

    // Reverse of escape().
    public static String unescape(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                // Next char tells us what was escaped.
                char n = s.charAt(++i);
                switch (n) {
                    case '\\': b.append('\\'); break;
                    case 't':  b.append('\t'); break;
                    case 'n':  b.append('\n'); break;
                    default:   b.append(n);    // unknown escape: keep the char
                }
            } else {
                b.append(c);
            }
        }
        return b.toString();
    }
}
