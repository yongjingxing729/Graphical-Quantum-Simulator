package com.gqs.test;

import com.gqs.json.Json;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** JSON 解析 / 序列化单元测试。 */
public final class JsonTest {

    private JsonTest() {
    }

    public static void runAll() {
        TestRunner.test("parse object", () -> {
            Object o = Json.parse("{\"a\":1,\"b\":\"x\"}");
            TestRunner.assertTrue("is map", o instanceof Map);
            Map<?, ?> m = (Map<?, ?>) o;
            TestRunner.assertEquals("a", 1L, m.get("a"));
            TestRunner.assertEquals("b", "x", m.get("b"));
        });

        TestRunner.test("parse array", () -> {
            Object o = Json.parse("[1,2,3]");
            List<?> l = (List<?>) o;
            TestRunner.assertEquals("size", 3, l.size());
            TestRunner.assertEquals("first", 1L, l.get(0));
        });

        TestRunner.test("parse nested circuit request", () -> {
            String src = "{\"steps\":[{\"gates\":[{\"type\":\"H\",\"targets\":[0]}]}]}";
            Map<?, ?> m = (Map<?, ?>) Json.parse(src);
            List<?> steps = (List<?>) m.get("steps");
            Map<?, ?> step = (Map<?, ?>) steps.get(0);
            List<?> gates = (List<?>) step.get("gates");
            Map<?, ?> gate = (Map<?, ?>) gates.get(0);
            TestRunner.assertEquals("type", "H", gate.get("type"));
            List<?> targets = (List<?>) gate.get("targets");
            TestRunner.assertEquals("target0", 0L, targets.get(0));
        });

        TestRunner.test("parse booleans and null", () -> {
            TestRunner.assertEquals("true", Boolean.TRUE, Json.parse("true"));
            TestRunner.assertEquals("false", Boolean.FALSE, Json.parse("false"));
            TestRunner.assertEquals("null", null, Json.parse("null"));
        });

        TestRunner.test("parse integer vs double", () -> {
            TestRunner.assertEquals("int", 3L, Json.parse("3"));
            Object d = Json.parse("1.5708");
            TestRunner.assertTrue("is double", d instanceof Double);
            TestRunner.assertClose("val", 1.5708, (Double) d, 1e-9);
        });

        TestRunner.test("parse negative and exponent", () -> {
            TestRunner.assertEquals("neg", -5L, Json.parse("-5"));
            TestRunner.assertClose("exp", 1000.0, (Double) Json.parse("1e3"), 1e-9);
        });

        TestRunner.test("parse escapes", () -> {
            TestRunner.assertEquals("quote", "a\"b", Json.parse("\"a\\\"b\""));
            TestRunner.assertEquals("backslash", "a\\b", Json.parse("\"a\\\\b\""));
            TestRunner.assertEquals("newline", "a\nb", Json.parse("\"a\\nb\""));
            TestRunner.assertEquals("unicode", "中", Json.parse("\"\\u4e2d\""));
        });

        TestRunner.test("parse utf8 literal", () -> {
            TestRunner.assertEquals("utf8", "量子", Json.parse("\"量子\""));
        });

        TestRunner.test("stringify map", () -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("a", 1L);
            m.put("b", "x\"y");
            String s = Json.stringify(m);
            TestRunner.assertEquals("json", "{\"a\":1,\"b\":\"x\\\"y\"}", s);
        });

        TestRunner.test("stringify list & null & boolean", () -> {
            TestRunner.assertEquals("list", "[1,2,3]", Json.stringify(List.of(1L, 2L, 3L)));
            TestRunner.assertEquals("null", "null", Json.stringify(null));
            TestRunner.assertEquals("bool", "true", Json.stringify(Boolean.TRUE));
        });

        TestRunner.test("round-trip", () -> {
            String src = "{\"qubits\":2,\"steps\":[{\"gates\":[{\"type\":\"CNOT\",\"targets\":[0,1]}]}]}";
            Object o = Json.parse(src);
            String s = Json.stringify(o);
            TestRunner.assertEquals("roundtrip", s, Json.stringify(Json.parse(s)));
        });

        TestRunner.test("invalid json throws", () -> {
            TestRunner.assertThrows("bad object", Json.JsonException.class, () -> Json.parse("{bad}"));
            TestRunner.assertThrows("unterminated", Json.JsonException.class, () -> Json.parse("\"abc"));
            TestRunner.assertThrows("trailing", Json.JsonException.class, () -> Json.parse("{}x"));
        });
    }
}
