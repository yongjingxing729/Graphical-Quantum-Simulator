package com.gqs.json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 极简 JSON 解析与序列化器（纯 JDK，无第三方依赖）。
 *
 * <p>对象模型：{@link Map}&lt;String,Object&gt;、{@link List}&lt;Object&gt;、
 * {@link String}、{@link Long}（整数）、{@link Double}（浮点）、{@link Boolean}、{@code null}。</p>
 */
public final class Json {

    private Json() {
    }

    // ------------------------------------------------------------------
    // 解析
    // ------------------------------------------------------------------

    /**
     * 解析 JSON 字符串为对象模型。
     *
     * @throws JsonException 当输入不是合法 JSON 时
     */
    public static Object parse(String s) {
        if (s == null) {
            throw new JsonException("Input is null");
        }
        Parser p = new Parser(s);
        Object v = p.parseValue();
        p.skipWhitespace();
        if (!p.eof()) {
            throw new JsonException("Unexpected trailing content at index " + p.pos);
        }
        return v;
    }

    // ------------------------------------------------------------------
    // 序列化
    // ------------------------------------------------------------------

    /**
     * 将对象模型序列化为 JSON 字符串。
     */
    public static String stringify(Object o) {
        StringBuilder sb = new StringBuilder();
        write(sb, o);
        return sb.toString();
    }

    private static void write(StringBuilder sb, Object o) {
        if (o == null) {
            sb.append("null");
        } else if (o instanceof String) {
            writeString(sb, (String) o);
        } else if (o instanceof Boolean) {
            sb.append(o.toString());
        } else if (o instanceof Number) {
            writeNumber(sb, (Number) o);
        } else if (o instanceof Map) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : ((Map<?, ?>) o).entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                writeString(sb, String.valueOf(e.getKey()));
                sb.append(':');
                write(sb, e.getValue());
            }
            sb.append('}');
        } else if (o instanceof List) {
            sb.append('[');
            boolean first = true;
            for (Object x : (List<?>) o) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                write(sb, x);
            }
            sb.append(']');
        } else {
            // 兜底：把任意对象按字符串输出
            writeString(sb, String.valueOf(o));
        }
    }

    private static void writeNumber(StringBuilder sb, Number n) {
        if (n instanceof Long || n instanceof Integer || n instanceof Short || n instanceof Byte) {
            sb.append(n.longValue());
        } else {
            sb.append(n.toString());
        }
    }

    private static void writeString(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
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

    // ------------------------------------------------------------------
    // 解析器实现
    // ------------------------------------------------------------------

    private static final class Parser {
        private final String s;
        private int pos;

        Parser(String s) {
            this.s = s;
            this.pos = 0;
        }

        boolean eof() {
            return pos >= s.length();
        }

        char peek() {
            return s.charAt(pos);
        }

        char next() {
            return s.charAt(pos++);
        }

        void skipWhitespace() {
            while (!eof()) {
                char c = s.charAt(pos);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    pos++;
                } else {
                    break;
                }
            }
        }

        Object parseValue() {
            skipWhitespace();
            if (eof()) {
                throw new JsonException("Unexpected end of input");
            }
            char c = peek();
            switch (c) {
                case '{': return parseObject();
                case '[': return parseArray();
                case '"': return parseString();
                case 't': expect("true"); return Boolean.TRUE;
                case 'f': expect("false"); return Boolean.FALSE;
                case 'n': expect("null"); return null;
                default: return parseNumber();
            }
        }

        Map<String, Object> parseObject() {
            next(); // '{'
            Map<String, Object> m = new LinkedHashMap<>();
            skipWhitespace();
            if (!eof() && peek() == '}') {
                next();
                return m;
            }
            while (true) {
                skipWhitespace();
                if (eof() || peek() != '"') {
                    throw new JsonException("Expected string key at index " + pos);
                }
                String key = parseString();
                skipWhitespace();
                if (eof() || peek() != ':') {
                    throw new JsonException("Expected ':' at index " + pos);
                }
                next();
                m.put(key, parseValue());
                skipWhitespace();
                char c = next();
                if (c == '}') {
                    return m;
                }
                if (c != ',') {
                    throw new JsonException("Expected ',' or '}' at index " + (pos - 1));
                }
            }
        }

        List<Object> parseArray() {
            next(); // '['
            List<Object> l = new ArrayList<>();
            skipWhitespace();
            if (!eof() && peek() == ']') {
                next();
                return l;
            }
            while (true) {
                l.add(parseValue());
                skipWhitespace();
                char c = next();
                if (c == ']') {
                    return l;
                }
                if (c != ',') {
                    throw new JsonException("Expected ',' or ']' at index " + (pos - 1));
                }
            }
        }

        String parseString() {
            next(); // '"'
            StringBuilder sb = new StringBuilder();
            while (true) {
                if (eof()) {
                    throw new JsonException("Unterminated string");
                }
                char c = next();
                if (c == '"') {
                    return sb.toString();
                }
                if (c == '\\') {
                    char e = next();
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
                            if (pos + 4 > s.length()) {
                                throw new JsonException("Invalid unicode escape");
                            }
                            String hex = s.substring(pos, pos + 4);
                            pos += 4;
                            sb.append((char) Integer.parseInt(hex, 16));
                            break;
                        default:
                            throw new JsonException("Invalid escape '\\" + e + "'");
                    }
                } else {
                    sb.append(c);
                }
            }
        }

        Object parseNumber() {
            int start = pos;
            if (!eof() && peek() == '-') {
                next();
            }
            while (!eof()) {
                char c = peek();
                if ((c >= '0' && c <= '9') || c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') {
                    next();
                } else {
                    break;
                }
            }
            String num = s.substring(start, pos);
            if (num.isEmpty() || "-".equals(num)) {
                throw new JsonException("Invalid number at index " + start);
            }
            try {
                double d = Double.parseDouble(num);
                boolean integral = !num.contains(".") && !num.contains("e") && !num.contains("E");
                if (integral && d == Math.floor(d) && Math.abs(d) < 9.007199254740992E15) {
                    return (long) d;
                }
                return d;
            } catch (NumberFormatException ex) {
                throw new JsonException("Invalid number '" + num + "'");
            }
        }

        void expect(String lit) {
            if (pos + lit.length() > s.length() || !s.startsWith(lit, pos)) {
                throw new JsonException("Unexpected token at index " + pos);
            }
            pos += lit.length();
        }
    }

    /** JSON 处理异常。 */
    public static final class JsonException extends RuntimeException {
        public JsonException(String message) {
            super(message);
        }
    }
}
