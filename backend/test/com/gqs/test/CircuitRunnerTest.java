package com.gqs.test;

import com.gqs.circuit.CircuitException;
import com.gqs.circuit.CircuitRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CircuitRunner 单元测试：验证「线路 JSON → Strange 模拟 → 结果」这一桥接链路，
 * 覆盖量子线路的物理正确性（Bell / GHZ / X / SWAP / Toffoli / 旋转门）、
 * 比特序、采样直方图，以及各类错误路径。
 */
@SuppressWarnings("unchecked")
public final class CircuitRunnerTest {

    private CircuitRunnerTest() {
    }

    public static void runAll() {

        // ---------------- 物理正确性 ----------------

        TestRunner.test("Bell state probabilities", () -> {
            Map<String, Object> r = CircuitRunner.run(request(2, 0,
                step(gate("H", 0)),
                step(gate("CNOT", 0, 1))));
            List<Double> p = probs(r);
            TestRunner.assertEquals("dim", 4, p.size());
            TestRunner.assertClose("p(00)", 0.5, p.get(0), 1e-4);
            TestRunner.assertClose("p(01)", 0.0, p.get(1), 1e-4);
            TestRunner.assertClose("p(10)", 0.0, p.get(2), 1e-4);
            TestRunner.assertClose("p(11)", 0.5, p.get(3), 1e-4);
        });

        TestRunner.test("Bell state amplitudes", () -> {
            Map<String, Object> r = CircuitRunner.run(request(2, 0,
                step(gate("H", 0)), step(gate("CNOT", 0, 1))));
            List<Map<String, Object>> sv = stateVector(r);
            TestRunner.assertClose("amp(00).re", 0.7071, num(sv.get(0).get("re")), 1e-3);
            TestRunner.assertClose("amp(11).re", 0.7071, num(sv.get(3).get("re")), 1e-3);
            TestRunner.assertClose("amp(01).re", 0.0, num(sv.get(1).get("re")), 1e-6);
        });

        TestRunner.test("single H creates superposition", () -> {
            Map<String, Object> r = CircuitRunner.run(request(1, 0, step(gate("H", 0))));
            TestRunner.assertClose("p0", 0.5, prob(r, 0), 1e-4);
            TestRunner.assertClose("p1", 0.5, prob(r, 1), 1e-4);
        });

        TestRunner.test("X flips qubit 0 -> state |01>", () -> {
            Map<String, Object> r = CircuitRunner.run(request(2, 0, step(gate("X", 0))));
            TestRunner.assertClose("p(01)", 1.0, prob(r, 1), 1e-6);
            TestRunner.assertClose("p(00)", 0.0, prob(r, 0), 1e-6);
        });

        TestRunner.test("X on qubit 1 -> state |10> (bit order)", () -> {
            Map<String, Object> r = CircuitRunner.run(request(2, 0, step(gate("X", 1))));
            TestRunner.assertClose("p(10)", 1.0, prob(r, 2), 1e-6);
        });

        TestRunner.test("empty program stays |0..0>", () -> {
            Map<String, Object> r = CircuitRunner.run(request(3, 0));
            TestRunner.assertClose("p(000)", 1.0, prob(r, 0), 1e-6);
            for (int i = 1; i < 8; i++) {
                TestRunner.assertClose("p(" + i + ")", 0.0, prob(r, i), 1e-6);
            }
        });

        TestRunner.test("GHZ-3", () -> {
            Map<String, Object> r = CircuitRunner.run(request(3, 0,
                step(gate("H", 0)),
                step(gate("CNOT", 0, 1)),
                step(gate("CNOT", 1, 2))));
            TestRunner.assertClose("p(000)", 0.5, prob(r, 0), 1e-4);
            TestRunner.assertClose("p(111)", 0.5, prob(r, 7), 1e-4);
        });

        TestRunner.test("Rx(pi/2) superposition", () -> {
            Map<String, Object> r = CircuitRunner.run(request(1, 0,
                step(gateAngle("RX", Math.PI / 2, 0))));
            TestRunner.assertClose("p0", 0.5, prob(r, 0), 1e-3);
            TestRunner.assertClose("p1", 0.5, prob(r, 1), 1e-3);
        });

        TestRunner.test("phase gate S on |+> keeps 50/50", () -> {
            Map<String, Object> r = CircuitRunner.run(request(1, 0,
                step(gate("H", 0)), step(gate("S", 0))));
            TestRunner.assertClose("p0", 0.5, prob(r, 0), 1e-4);
            TestRunner.assertClose("p1", 0.5, prob(r, 1), 1e-4);
        });

        TestRunner.test("SWAP exchanges amplitudes", () -> {
            Map<String, Object> r = CircuitRunner.run(request(2, 0,
                step(gate("X", 1)),
                step(gate("SWAP", 0, 1))));
            TestRunner.assertClose("p(01)", 1.0, prob(r, 1), 1e-6);
        });

        TestRunner.test("Toffoli flips target when both controls set", () -> {
            Map<String, Object> r = CircuitRunner.run(request(3, 0,
                step(gate("X", 0), gate("X", 1)),
                step(gate("TOFFOLI", 0, 1, 2))));
            TestRunner.assertClose("p(111)", 1.0, prob(r, 7), 1e-6);
        });

        TestRunner.test("parallel gates in one step", () -> {
            // 同一 Step 内不同比特并行作用，等价于两比特各自翻转
            Map<String, Object> r = CircuitRunner.run(request(2, 0,
                step(gate("X", 0), gate("X", 1))));
            TestRunner.assertClose("p(11)", 1.0, prob(r, 3), 1e-6);
        });

        // ---------------- 采样 / 比特概率 ----------------

        TestRunner.test("shots histogram sums to shots (Bell)", () -> {
            Map<String, Object> r = CircuitRunner.run(request(2, 200,
                step(gate("H", 0)), step(gate("CNOT", 0, 1))));
            Map<String, Integer> c = counts(r);
            int sum = 0;
            for (int v : c.values()) {
                sum += v;
            }
            TestRunner.assertEquals("shots sum", 200, sum);
            TestRunner.assertTrue("has 00", c.containsKey("00"));
            TestRunner.assertTrue("has 11", c.containsKey("11"));
            TestRunner.assertFalse("no 01", c.containsKey("01"));
            TestRunner.assertFalse("no 10", c.containsKey("10"));
        });

        TestRunner.test("shots=0 returns empty counts", () -> {
            Map<String, Object> r = CircuitRunner.run(request(1, 0, step(gate("H", 0))));
            TestRunner.assertEquals("empty counts", 0, counts(r).size());
        });

        TestRunner.test("qubit probabilities", () -> {
            Map<String, Object> r = CircuitRunner.run(request(2, 0,
                step(gate("H", 0)), step(gate("CNOT", 0, 1))));
            List<Double> qp = (List<Double>) r.get("qubitProbabilities");
            TestRunner.assertEquals("size", 2, qp.size());
            TestRunner.assertClose("q0", 0.5, qp.get(0), 1e-4);
            TestRunner.assertClose("q1", 0.5, qp.get(1), 1e-4);
        });

        TestRunner.test("result reports mode", () -> {
            Map<String, Object> r = CircuitRunner.run(request(1, 0, step(gate("H", 0))));
            TestRunner.assertEquals("mode", "local", r.get("mode"));
        });

        // ---------------- 输入态 ----------------

        TestRunner.test("input state |1> on q0", () -> {
            Map<String, Object> r = CircuitRunner.run(requestWithInputs(2, 0, List.of("1", "0")));
            TestRunner.assertClose("p(01)", 1.0, prob(r, 1), 1e-6);
            TestRunner.assertEquals("inputs echoed", List.of("1", "0"), r.get("inputs"));
        });

        TestRunner.test("input state |+> superposition", () -> {
            Map<String, Object> r = CircuitRunner.run(requestWithInputs(1, 0, List.of("+")));
            TestRunner.assertClose("p0", 0.5, prob(r, 0), 1e-4);
            TestRunner.assertClose("p1", 0.5, prob(r, 1), 1e-4);
        });

        TestRunner.test("input probabilities returned", () -> {
            Map<String, Object> r = CircuitRunner.run(requestWithInputs(1, 0, List.of("+")));
            List<Double> ip = (List<Double>) r.get("inputProbabilities");
            TestRunner.assertEquals("size", 2, ip.size());
            TestRunner.assertClose("ip0", 0.5, ip.get(0), 1e-4);
            TestRunner.assertClose("ip1", 0.5, ip.get(1), 1e-4);
        });

        TestRunner.test("input |1> then X returns |0>", () -> {
            Map<String, Object> r = CircuitRunner.run(requestWithInputs(1, 0, List.of("1"),
                step(gate("X", 0))));
            TestRunner.assertClose("p0", 1.0, prob(r, 0), 1e-6);
        });

        TestRunner.test("input |i> gives pure imaginary amplitude", () -> {
            Map<String, Object> r = CircuitRunner.run(requestWithInputs(1, 0, List.of("i")));
            List<Map<String, Object>> sv = stateVector(r);
            TestRunner.assertClose("amp0.re", 0.7071, num(sv.get(0).get("re")), 1e-3);
            TestRunner.assertClose("amp1.im", 0.7071, num(sv.get(1).get("im")), 1e-3);
        });

        TestRunner.test("invalid input state throws", () -> {
            TestRunner.assertThrows("x", CircuitException.class,
                () -> CircuitRunner.run(requestWithInputs(1, 0, List.of("x"))));
        });

        TestRunner.test("inputs length mismatch throws", () -> {
            TestRunner.assertThrows("len", CircuitException.class,
                () -> CircuitRunner.run(requestWithInputs(2, 0, List.of("0"))));
        });

        TestRunner.test("target out of range throws", () -> {
            TestRunner.assertThrows("oor", CircuitException.class,
                () -> CircuitRunner.run(request(2, 0, step(gate("X", 5)))));
        });

        TestRunner.test("error message references provided col", () -> {
            Map<String, Object> step = new LinkedHashMap<>();
            step.put("col", 5);
            Map<String, Object> gate = new LinkedHashMap<>();
            gate.put("type", "X");
            gate.put("targets", List.of(99));
            step.put("gates", List.of(gate));
            Map<String, Object> req = new LinkedHashMap<>();
            req.put("qubits", 2);
            req.put("shots", 0);
            req.put("steps", List.of(step));
            try {
                CircuitRunner.run(req);
                throw new AssertionError("应当抛出异常");
            } catch (CircuitException e) {
                TestRunner.assertTrue("message 含 t5: " + e.getMessage(),
                    e.getMessage().contains("t5"));
            }
        });

        // ---------------- 错误路径 ----------------

        TestRunner.test("invalid qubit count", () -> {
            TestRunner.assertThrows("0 qubits", CircuitException.class,
                () -> CircuitRunner.run(request(0, 0)));
            TestRunner.assertThrows("13 qubits", CircuitException.class,
                () -> CircuitRunner.run(request(13, 0)));
        });

        TestRunner.test("steps over MAX_STEPS throws", () -> {
            List<Object> many = new ArrayList<>();
            for (int i = 0; i < 10001; i++) {
                many.add(new LinkedHashMap<>());
            }
            Map<String, Object> req = new LinkedHashMap<>();
            req.put("qubits", 1);
            req.put("shots", 0);
            req.put("steps", many);
            TestRunner.assertThrows("too many steps", CircuitException.class,
                () -> CircuitRunner.run(req));
        });

        TestRunner.test("invalid shots", () -> {
            TestRunner.assertThrows("negative shots", CircuitException.class,
                () -> CircuitRunner.run(request(1, -1)));
            TestRunner.assertThrows("too many shots", CircuitException.class,
                () -> CircuitRunner.run(request(1, 200000)));
        });

        TestRunner.test("shots work limit", () -> {
            // 12 比特 × 10 万 shots = 409.6M > 上限 200M
            TestRunner.assertThrows("too much work", CircuitException.class,
                () -> CircuitRunner.run(request(12, 100000, step(gate("H", 0)))));
        });

        TestRunner.test("angle out of range throws", () -> {
            TestRunner.assertThrows("huge angle", CircuitException.class,
                () -> CircuitRunner.run(request(1, 0, step(gateAngle("RX", 1e100, 0)))));
        });

        TestRunner.test("gate overlap in one step throws", () -> {
            TestRunner.assertThrows("H(q0)+CNOT(q0,q1) same step", CircuitException.class,
                () -> CircuitRunner.run(request(2, 0,
                    step(gate("H", 0), gate("CNOT", 0, 1)))));
        });

        TestRunner.test("superposition after measurement throws", () -> {
            TestRunner.assertThrows("measure then H", CircuitException.class,
                () -> CircuitRunner.run(request(2, 0,
                    step(gate("M", 0)), step(gate("H", 0)))));
        });

        TestRunner.test("unsupported gate throws", () -> {
            TestRunner.assertThrows("FOO", CircuitException.class,
                () -> CircuitRunner.run(request(1, 0, step(gate("FOO", 0)))));
        });

        TestRunner.test("missing targets throws", () -> {
            Map<String, Object> badGate = new LinkedHashMap<>();
            badGate.put("type", "H");
            Map<String, Object> badStep = new LinkedHashMap<>();
            badStep.put("gates", List.of(badGate));
            Map<String, Object> req = new LinkedHashMap<>();
            req.put("qubits", 1);
            req.put("shots", 0);
            req.put("steps", List.of(badStep));
            TestRunner.assertThrows("no targets", CircuitException.class,
                () -> CircuitRunner.run(req));
        });
    }

    // ------------------------------------------------------------------
    // 请求构造辅助
    // ------------------------------------------------------------------

    private static Map<String, Object> gate(String type, int... targets) {
        Map<String, Object> g = new LinkedHashMap<>();
        g.put("type", type);
        g.put("targets", ints(targets));
        return g;
    }

    private static Map<String, Object> gateAngle(String type, double angle, int... targets) {
        Map<String, Object> g = gate(type, targets);
        g.put("angle", angle);
        return g;
    }

    @SafeVarargs
    private static Map<String, Object> step(Map<String, Object>... gates) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("gates", new ArrayList<>(Arrays.asList(gates)));
        return s;
    }

    @SafeVarargs
    private static Map<String, Object> request(int qubits, int shots, Map<String, Object>... steps) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("qubits", qubits);
        r.put("shots", shots);
        r.put("steps", new ArrayList<>(Arrays.asList(steps)));
        return r;
    }

    @SafeVarargs
    private static Map<String, Object> requestWithInputs(int qubits, int shots, List<String> inputs,
            Map<String, Object>... steps) {
        Map<String, Object> r = request(qubits, shots, steps);
        r.put("inputs", inputs);
        return r;
    }

    private static List<Integer> ints(int... values) {
        List<Integer> l = new ArrayList<>();
        for (int v : values) {
            l.add(v);
        }
        return l;
    }

    // ------------------------------------------------------------------
    // 结果读取辅助
    // ------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private static List<Double> probs(Map<String, Object> r) {
        return (List<Double>) r.get("probabilities");
    }

    private static double prob(Map<String, Object> r, int index) {
        return probs(r).get(index);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Integer> counts(Map<String, Object> r) {
        return (Map<String, Integer>) r.get("counts");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> stateVector(Map<String, Object> r) {
        return (List<Map<String, Object>>) r.get("stateVector");
    }

    private static double num(Object o) {
        return ((Number) o).doubleValue();
    }
}
