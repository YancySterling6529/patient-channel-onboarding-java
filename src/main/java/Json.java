import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class Json {
    private Json() {}

    static String write(Object value) {
        if (value == null) return "null";
        if (value instanceof String text) return '"' + escape(text) + '"';
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        if (value instanceof Map<?, ?> map) {
            StringBuilder out = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) out.append(',');
                first = false;
                out.append(write(String.valueOf(entry.getKey()))).append(':').append(write(entry.getValue()));
            }
            return out.append('}').toString();
        }
        if (value instanceof Iterable<?> values) {
            StringBuilder out = new StringBuilder("[");
            boolean first = true;
            for (Object item : values) {
                if (!first) out.append(',');
                first = false;
                out.append(write(item));
            }
            return out.append(']').toString();
        }
        throw new IllegalArgumentException("Unsupported JSON value: " + value.getClass());
    }

    static Map<String, Object> readObject(String text) {
        Object parsed = new Parser(text).value();
        if (!(parsed instanceof Map<?, ?> map)) throw new IllegalArgumentException("Expected JSON object");
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private static String escape(String text) {
        StringBuilder out = new StringBuilder();
        for (char c : text.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        }
        return out.toString();
    }

    private static final class Parser {
        private final String text;
        private int position;

        Parser(String text) { this.text = text; }

        Object value() {
            whitespace();
            if (position >= text.length()) throw new IllegalArgumentException("Unexpected end of JSON");
            char c = text.charAt(position);
            Object result = switch (c) {
                case '{' -> object();
                case '[' -> array();
                case '"' -> string();
                case 't' -> literal("true", true);
                case 'f' -> literal("false", false);
                case 'n' -> literal("null", null);
                default -> number();
            };
            whitespace();
            return result;
        }

        private Map<String, Object> object() {
            Map<String, Object> out = new LinkedHashMap<>();
            position++;
            whitespace();
            if (take('}')) return out;
            do {
                whitespace();
                String key = string();
                whitespace();
                expect(':');
                out.put(key, value());
                whitespace();
            } while (take(','));
            expect('}');
            return out;
        }

        private List<Object> array() {
            List<Object> out = new ArrayList<>();
            position++;
            whitespace();
            if (take(']')) return out;
            do out.add(value()); while (take(','));
            expect(']');
            return out;
        }

        private String string() {
            expect('"');
            StringBuilder out = new StringBuilder();
            while (position < text.length()) {
                char c = text.charAt(position++);
                if (c == '"') return out.toString();
                if (c != '\\') {
                    out.append(c);
                    continue;
                }
                char escaped = text.charAt(position++);
                switch (escaped) {
                    case '"', '\\', '/' -> out.append(escaped);
                    case 'b' -> out.append('\b');
                    case 'f' -> out.append('\f');
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case 'u' -> {
                        out.append((char) Integer.parseInt(text.substring(position, position + 4), 16));
                        position += 4;
                    }
                    default -> throw new IllegalArgumentException("Invalid JSON escape");
                }
            }
            throw new IllegalArgumentException("Unterminated JSON string");
        }

        private Object number() {
            int start = position;
            while (position < text.length() && "-+0123456789.eE".indexOf(text.charAt(position)) >= 0) position++;
            String number = text.substring(start, position);
            return number.contains(".") || number.contains("e") || number.contains("E")
                    ? Double.parseDouble(number) : Long.parseLong(number);
        }

        private Object literal(String expected, Object value) {
            if (!text.startsWith(expected, position)) throw new IllegalArgumentException("Invalid JSON literal");
            position += expected.length();
            return value;
        }

        private void whitespace() {
            while (position < text.length() && Character.isWhitespace(text.charAt(position))) position++;
        }

        private boolean take(char expected) {
            whitespace();
            if (position < text.length() && text.charAt(position) == expected) {
                position++;
                return true;
            }
            return false;
        }

        private void expect(char expected) {
            if (!take(expected)) throw new IllegalArgumentException("Expected '" + expected + "'");
        }
    }
}
