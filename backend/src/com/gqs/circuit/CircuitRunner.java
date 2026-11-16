package com.gqs.circuit;

import com.gqs.AppConfig;
import org.redfx.strange.Complex;
import org.redfx.strange.Gate;
import org.redfx.strange.Program;
import org.redfx.strange.QuantumExecutionEnvironment;
import org.redfx.strange.Qubit;
import org.redfx.strange.Result;
import org.redfx.strange.Step;
import org.redfx.strange.local.SimpleQuantumExecutionEnvironment;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 量子线路执行器。
 *
 * <p>职责：把前端传来的线路 JSON 构建为 Strange 的 {@link Program}，交给
 * {@link QuantumExecutionEnvironment}（本地模拟）执行，并组装为可视化结果。</p>
 *
 * <p>支持「自定义输入态」：通过准备门（X/H/Z/S/P）把每个量子比特从 |0⟩ 制备为
 * |0⟩/|1⟩/|+⟩/|−⟩/|i⟩/|−i⟩，再叠加用户线路。输入态与输出态都会返回给前端展示。</p>
 *
 * <p><strong>核心模拟逻辑完全来自上游 Strange 库</strong>，本类仅做数据组装与结果提取，
 * 不改动上游任何模拟代码。</p>
 */
public final class CircuitRunner {

    /** 合法的单比特输入态。 */
    private static final String[] VALID_INPUTS = {"0", "1", "+", "-", "i", "-i"};

    private CircuitRunner() {
    }

    /**
     * 执行一次线路模拟。
     *
     * @param req 请求对象：{qubits, shots, inputs:[...], steps:[{gates:[{type, targets:[...], angle?}]}]}
     * @return 结果对象（含输入态/输出态的态矢量、概率、按比特概率、测量采样直方图）
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> run(Map<String, Object> req) {
        int qubits = intOf(req.get("qubits"), 2);
        if (qubits < 1 || qubits > AppConfig.MAX_QUBITS) {
            throw new CircuitException("量子比特数必须在 1 到 " + AppConfig.MAX_QUBITS + " 之间");
        }
        int shots = intOf(req.get("shots"), 1024);
        if (shots < 0 || shots > 100000) {
            throw new CircuitException("采样次数必须在 0 到 100000 之间");
        }

        // 0) 解析输入态（默认全 |0⟩）
        List<String> inputs = parseInputs(req.get("inputs"), qubits);

        // 1) 输入态：仅准备门（全 |0⟩ 时直接构造，避免多余计算）
        Complex[] inputSv = initialInputVector(inputs, qubits);

        // 2) 构建完整 Program：准备门（输入态） + 用户线路
        Program program = new Program(qubits);
        List<Step> prepSteps = buildPrepSteps(inputs, qubits);
        for (Step s : prepSteps) {
            program.addStep(s); // 准备门不含测量，不会触发 measuresafe 校验
        }

        List<Step> userSteps = buildUserSteps(req.get("steps"), qubits);
        for (int i = 0; i < userSteps.size(); i++) {
            try {
                program.addStep(userSteps.get(i));
            } catch (IllegalArgumentException e) {
                throw new CircuitException(
                    "列 t" + i + " 无效：" + e.getMessage()
                        + "（量子测量不可逆：被测比特之后不能再施加会产生叠加态的 H 或 CNOT 目标）");
            }
        }

        // 3) 执行
        QuantumExecutionEnvironment qee = new SimpleQuantumExecutionEnvironment();
        Result result;
        try {
            result = qee.runProgram(program);
        } catch (RuntimeException e) {
            throw new CircuitException("模拟执行失败：" + e.getMessage(), e);
        }

        // 4) 提取输出态
        Complex[] sv = result.getProbability();
        Qubit[] qubitsArr = result.getQubits();
        List<Double> qubitProbs = new ArrayList<>(qubits);
        for (Qubit q : qubitsArr) {
            qubitProbs.add(round(q.getProbability()));
        }

        // 5) 采样测量
        Map<String, Integer> counts = new LinkedHashMap<>();
        String sample = null;
        for (int s = 0; s < shots; s++) {
            result.measureSystem();
            int m = result.getMeasuredProbability();
            if (s == 0) {
                sample = toBits(m, qubits);
            }
            counts.merge(toBits(m, qubits), 1, Integer::sum);
        }

        // 6) 组装结果
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("qubits", qubits);
        out.put("mode", AppConfig.mode());
        out.put("inputs", inputs);
        putState(out, inputSv, qubits, "inputStateVector", "inputProbabilities");
        putState(out, sv, qubits, "stateVector", "probabilities");
        out.put("qubitProbabilities", qubitProbs);
        out.put("shots", shots);
        out.put("counts", counts);
        if (sample != null) {
            out.put("sample", sample);
        }
        return out;
    }

    // ------------------------------------------------------------------
    // 输入态准备
    // ------------------------------------------------------------------

    private static List<String> parseInputs(Object raw, int qubits) {
        List<String> inputs = new ArrayList<>(qubits);
        if (raw instanceof List) {
            List<?> l = (List<?>) raw;
            if (l.size() != qubits) {
                throw new CircuitException(
                    "'inputs' 长度 " + l.size() + " 与量子比特数 " + qubits + " 不一致");
            }
            for (int i = 0; i < qubits; i++) {
                String v = String.valueOf(l.get(i)).trim();
                if (!isValidInput(v)) {
                    throw new CircuitException(
                        "第 " + i + " 个量子比特的输入态 '" + v + "' 非法（可选：0, 1, +, -, i, -i）");
                }
                inputs.add(v);
            }
        } else {
            for (int i = 0; i < qubits; i++) {
                inputs.add("0");
            }
        }
        return inputs;
    }

    private static boolean isValidInput(String v) {
        for (String s : VALID_INPUTS) {
            if (s.equals(v)) {
                return true;
            }
        }
        return false;
    }

    private static Complex[] initialInputVector(List<String> inputs, int qubits) {
        boolean anyNonZero = false;
        for (String s : inputs) {
            if (!"0".equals(s)) {
                anyNonZero = true;
                break;
            }
        }
        if (!anyNonZero) {
            return zeroStateVector(qubits);
        }
        Program p = new Program(qubits);
        for (Step s : buildPrepSteps(inputs, qubits)) {
            p.addStep(s);
        }
        try {
            return new SimpleQuantumExecutionEnvironment().runProgram(p).getProbability();
        } catch (RuntimeException e) {
            throw new CircuitException("构建输入态失败：" + e.getMessage(), e);
        }
    }

    private static Complex[] zeroStateVector(int qubits) {
        int dim = 1 << qubits;
        Complex[] sv = new Complex[dim];
        for (int i = 0; i < dim; i++) {
            sv[i] = (i == 0) ? Complex.ONE : Complex.ZERO;
        }
        return sv;
    }

    /** 为每个比特生成准备门序列，并按「深度」打包成 Step（同一 Step 内不同比特并行）。 */
    private static List<Step> buildPrepSteps(List<String> inputs, int qubits) {
        List<List<Gate>> seqs = new ArrayList<>(qubits);
        for (int q = 0; q < qubits; q++) {
            seqs.add(prepGates(inputs.get(q), q));
        }
        int maxDepth = 0;
        for (List<Gate> s : seqs) {
            maxDepth = Math.max(maxDepth, s.size());
        }
        List<Step> steps = new ArrayList<>();
        for (int d = 0; d < maxDepth; d++) {
            Step step = new Step();
            for (int q = 0; q < qubits; q++) {
                List<Gate> s = seqs.get(q);
                if (d < s.size()) {
                    step.addGate(s.get(d));
                }
            }
            steps.add(step);
        }
        return steps;
    }

    /** 输入态 → 准备门序列（均为可逆酉门，符合量子计算可逆性）。 */
    private static List<Gate> prepGates(String state, int qubit) {
        switch (state) {
            case "0":
                return List.of();
            case "1":
                return List.of(GateFactory.create("X", new int[] {qubit}, 0));
            case "+":
                return List.of(GateFactory.create("H", new int[] {qubit}, 0));
            case "-": // H 后接 Z：|0⟩ --H--> |+⟩ --Z--> |−⟩
                return List.of(
                    GateFactory.create("H", new int[] {qubit}, 0),
                    GateFactory.create("Z", new int[] {qubit}, 0));
            case "i": // H 后接 S：|0⟩ --H--> |+⟩ --S--> |i⟩
                return List.of(
                    GateFactory.create("H", new int[] {qubit}, 0),
                    GateFactory.create("S", new int[] {qubit}, 0));
            case "-i": // H 后接 R(-π/2)：得到 |−i⟩
                return List.of(
                    GateFactory.create("H", new int[] {qubit}, 0),
                    GateFactory.create("P", new int[] {qubit}, -Math.PI / 2));
            default:
                throw new CircuitException("输入态 '" + state + "' 非法");
        }
    }

    // ------------------------------------------------------------------
    // 用户线路解析
    // ------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private static List<Step> buildUserSteps(Object stepsRaw, int qubits) {
        List<Step> steps = new ArrayList<>();
        if (stepsRaw == null) {
            return steps;
        }
        if (!(stepsRaw instanceof List)) {
            throw new CircuitException("'steps' 必须是数组");
        }
        int stepIndex = 0;
        for (Object stepObj : (List<Object>) stepsRaw) {
            if (!(stepObj instanceof Map)) {
                throw new CircuitException("列 t" + stepIndex + " 必须是对象");
            }
            Map<String, Object> stepMap = (Map<String, Object>) stepObj;
            Step step = new Step();
            Object gatesRaw = stepMap.get("gates");
            if (gatesRaw != null) {
                if (!(gatesRaw instanceof List)) {
                    throw new CircuitException("列 t" + stepIndex + " 的 gates 必须是数组");
                }
                int gateIndex = 0;
                for (Object gObj : (List<Object>) gatesRaw) {
                    if (!(gObj instanceof Map)) {
                        throw new CircuitException(
                            "列 t" + stepIndex + " 第 " + gateIndex + " 个门必须是对象");
                    }
                    Map<String, Object> g = (Map<String, Object>) gObj;
                    String type = str(g.get("type"));
                    int[] targets = intArray(g.get("targets"));
                    for (int t : targets) {
                        if (t < 0 || t >= qubits) {
                            throw new CircuitException(
                                "门 '" + type + "' 的比特下标 " + t
                                    + " 超出范围 [0, " + (qubits - 1) + "]");
                        }
                    }
                    double angle = doubleOf(g.get("angle"), 0.0);
                    try {
                        step.addGate(GateFactory.create(type, targets, angle));
                    } catch (IllegalArgumentException e) {
                        throw new CircuitException(
                            "列 t" + stepIndex + "，门 '" + type + "'：" + e.getMessage());
                    }
                    gateIndex++;
                }
            }
            steps.add(step);
            stepIndex++;
        }
        return steps;
    }

    // ------------------------------------------------------------------
    // 工具方法
    // ------------------------------------------------------------------

    private static void putState(
            Map<String, Object> out, Complex[] sv, int qubits, String stateKey, String probKey) {
        int dim = (sv == null) ? (1 << qubits) : sv.length;
        List<Map<String, Object>> stateVector = new ArrayList<>(dim);
        List<Double> probabilities = new ArrayList<>(dim);
        for (int k = 0; k < dim; k++) {
            Complex c = sv[k];
            double re = (c == null) ? 0.0 : c.r;
            double im = (c == null) ? 0.0 : c.i;
            stateVector.add(amplitude(re, im));
            probabilities.add(round(re * re + im * im));
        }
        out.put(stateKey, stateVector);
        out.put(probKey, probabilities);
    }

    private static Map<String, Object> amplitude(double re, double im) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("re", round(re));
        m.put("im", round(im));
        return m;
    }

    /** 整数 → n 比特字符串（LSB 为 q0，右侧对齐显示）。 */
    static String toBits(int value, int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = n - 1; i >= 0; i--) {
            sb.append(((value >> i) & 1) == 1 ? '1' : '0');
        }
        return sb.toString();
    }

    private static int intOf(Object o, int def) {
        if (o instanceof Number) {
            return ((Number) o).intValue();
        }
        if (o instanceof String) {
            try {
                return Integer.parseInt(((String) o).trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return def;
    }

    private static double doubleOf(Object o, double def) {
        if (o instanceof Number) {
            return ((Number) o).doubleValue();
        }
        if (o instanceof String) {
            try {
                return Double.parseDouble(((String) o).trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return def;
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static int[] intArray(Object o) {
        if (o instanceof List) {
            List<?> l = (List<?>) o;
            int[] a = new int[l.size()];
            for (int i = 0; i < l.size(); i++) {
                Object x = l.get(i);
                if (x instanceof Number) {
                    a[i] = ((Number) x).intValue();
                } else {
                    a[i] = Integer.parseInt(String.valueOf(x).trim());
                }
            }
            return a;
        }
        throw new CircuitException("'targets' 必须是比特下标数组");
    }

    /** 保留 6 位小数并清理 -0.0。 */
    private static double round(double v) {
        if (Math.abs(v) < 1e-9) {
            return 0.0;
        }
        return Math.round(v * 1_000_000.0) / 1_000_000.0;
    }
}
