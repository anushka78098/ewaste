package com.ewaste.common;

import java.util.*;

/** Minimal JSON reader/writer (objects -> LinkedHashMap, arrays -> ArrayList, integers -> Long, others -> Double). */
public final class Json {
    private Json() {}

    public static class JsonException extends IllegalArgumentException {
        private static final long serialVersionUID = 1L;
        public JsonException(String m) { super(m); }
    }

    // ------------------------------------------------------------------ writing
    public static String write(Object o) {
        StringBuilder sb = new StringBuilder();
        write(o, sb);
        return sb.toString();
    }

    private static void write(Object o, StringBuilder sb) {
        if (o == null) sb.append("null");
        else if (o instanceof String) quote((String) o, sb);
        else if (o instanceof Boolean) sb.append(o);
        else if (o instanceof Number) {
            double d = ((Number) o).doubleValue();
            if (o instanceof Double || o instanceof Float) {
                if (Double.isNaN(d) || Double.isInfinite(d)) sb.append("null");
                else if (d == Math.rint(d) && Math.abs(d) < 1e15) sb.append(String.format(Locale.ROOT, "%.1f", d));
                else sb.append(Double.toString(d));
            } else sb.append(o);
        } else if (o instanceof Enum) quote(((Enum<?>) o).name(), sb);
        else if (o instanceof Map) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : ((Map<?, ?>) o).entrySet()) {
                if (!first) sb.append(',');
                first = false;
                quote(String.valueOf(e.getKey()), sb);
                sb.append(':');
                write(e.getValue(), sb);
            }
            sb.append('}');
        } else if (o instanceof Iterable) {
            sb.append('[');
            boolean first = true;
            for (Object x : (Iterable<?>) o) {
                if (!first) sb.append(',');
                first = false;
                write(x, sb);
            }
            sb.append(']');
        } else quote(o.toString(), sb);
    }

    private static void quote(String s, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        sb.append('"');
    }

    // ------------------------------------------------------------------ reading
    public static Object parse(String text) {
        Parser p = new Parser(text);
        p.ws();
        Object v = p.value();
        p.ws();
        if (p.i != text.length()) throw new JsonException("Unexpected data after JSON value");
        return v;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String text) {
        if (text == null || text.trim().isEmpty()) return new LinkedHashMap<>();
        Object o = parse(text);
        if (!(o instanceof Map)) throw new JsonException("Expected a JSON object");
        return (Map<String, Object>) o;
    }

    private static final class Parser {
        final String s;
        int i;
        Parser(String s) { this.s = s; }

        void ws() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }

        char peek() {
            if (i >= s.length()) throw new JsonException("Unexpected end of JSON");
            return s.charAt(i);
        }

        Object value() {
            char c = peek();
            if (c == '{') return object();
            if (c == '[') return array();
            if (c == '"') return string();
            if (s.startsWith("true", i)) { i += 4; return Boolean.TRUE; }
            if (s.startsWith("false", i)) { i += 5; return Boolean.FALSE; }
            if (s.startsWith("null", i)) { i += 4; return null; }
            return number();
        }

        Map<String, Object> object() {
            Map<String, Object> m = new LinkedHashMap<>();
            i++; ws();
            if (peek() == '}') { i++; return m; }
            while (true) {
                ws();
                if (peek() != '"') throw new JsonException("Expected a string key at position " + i);
                String k = string();
                ws();
                if (peek() != ':') throw new JsonException("Expected ':' at position " + i);
                i++; ws();
                m.put(k, value());
                ws();
                char c = peek();
                i++;
                if (c == '}') return m;
                if (c != ',') throw new JsonException("Expected ',' or '}' at position " + (i - 1));
            }
        }

        List<Object> array() {
            List<Object> l = new ArrayList<>();
            i++; ws();
            if (peek() == ']') { i++; return l; }
            while (true) {
                ws();
                l.add(value());
                ws();
                char c = peek();
                i++;
                if (c == ']') return l;
                if (c != ',') throw new JsonException("Expected ',' or ']' at position " + (i - 1));
            }
        }

        String string() {
            StringBuilder sb = new StringBuilder();
            i++;
            while (true) {
                char c = peek();
                i++;
                if (c == '"') return sb.toString();
                if (c != '\\') { sb.append(c); continue; }
                char e = peek();
                i++;
                switch (e) {
                    case '"': sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    case '/': sb.append('/'); break;
                    case 'b': sb.append('\b'); break;
                    case 'f': sb.append('\f'); break;
                    case 'n': sb.append('\n'); break;
                    case 'r': sb.append('\r'); break;
                    case 't': sb.append('\t'); break;
                    case 'u':
                        if (i + 4 > s.length()) throw new JsonException("Bad unicode escape");
                        try { sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); }
                        catch (NumberFormatException ex) { throw new JsonException("Bad unicode escape"); }
                        i += 4;
                        break;
                    default: throw new JsonException("Bad escape \\" + e);
                }
            }
        }

        Object number() {
            int st = i;
            while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
            String t = s.substring(st, i);
            if (t.isEmpty()) throw new JsonException("Unexpected character '" + s.charAt(st) + "' at position " + st);
            try {
                if (t.contains(".") || t.contains("e") || t.contains("E")) return Double.valueOf(t);
                return Long.valueOf(t);
            } catch (NumberFormatException ex) {
                throw new JsonException("Bad number '" + t + "'");
            }
        }
    }
}
