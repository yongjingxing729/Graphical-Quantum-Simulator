package com.gqs.circuit;

import com.gqs.AppConfig;
import org.redfx.strange.Complex;
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
 * <p><strong>核心模拟逻辑完全来自上游 Strange 库</strong>，本类仅做数据组装与结果提取，
 * 不改动上游任何模拟代码。</p>
 */
public final class CircuitRunner {

    private CircuitRunner() {
    }

    /**
     * 执行一次线路模拟。
     *
     * @param req 请求对象：{qubits, shots, steps:[{gates:[{type, targets:[...], angle?}]}]}
     * @return 结果对象（含态矢量、概率、按比特概率、测量采样直方图）
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> run(Map<String, Object> req) {
        int qubits = intOf(req.get("qubits"), 2);
        if (qubits < 1 || qubits > AppConfig.MAX_QUBITS) {
            throw new CircuitException("Qubit count must be between 1 and " + AppConfig.MAX_QUBITS);
        }
        int shots = intOf(req.get("shots"), 1024);
        if (shots < 0 || shots > 100000) {
            throw new CircuitException("Shots must be between 0 and 100000");
        }

        // 1) 构建 Strange Program
        Program program = new Program(qubits);
        Object stepsRaw = req.get("steps");
        if (stepsRaw != null) {
            if (!(stepsRaw instanceof List)) {
                throw new CircuitException("'steps' must be an array");
            }
            int stepIndex = 0;
            for (Object stepObj : (List<Object>) stepsRaw) {
                if (!(stepObj instanceof Map)) {
                    throw new CircuitException("Step at index " + stepIndex + " must be an object");
                }
                Map<String, Object> stepMap = (Map<String, Object>) stepObj;
                Step step = new Step();
                Object gatesRaw = stepMap.get("gates");
                if (gatesRaw != null) {
                    if (!(gatesRaw instanceof List)) {
                        throw new CircuitException("Step " + stepIndex + " 'gates' must be an array");
                    }
                    int gateIndex = 0;
                    for (Object gObj : (List<Object>) gatesRaw) {
                        if (!(gObj instanceof Map)) {
                            throw new CircuitException(
                                "Gate at step " + stepIndex + " index " + gateIndex + " must be an object");
                        }
                        Map<String, Object> g = (Map<String, Object>) gObj;
                        String type = str(g.get("type"));
                        int[] targets = intArray(g.get("targets"));
                        double angle = doubleOf(g.get("angle"), 0.0);
                        try {
                            step.addGate(GateFactory.create(type, targets, angle));
                        } catch (IllegalArgumentException e) {
                            throw new CircuitException(
                                "Step " + stepIndex + ", gate '" + type + "': " + e.getMessage());
                        }
                        gateIndex++;
                    }
                }
                try {
                    program.addStep(step);
                } catch (IllegalArgumentException e) {
                    throw new CircuitException(
                        "Step " + stepIndex + " is invalid: " + e.getMessage()
                            + " (可能是在测量后的量子比特上施加了会产生叠加态的 H / CNOT 门)");
                }
                stepIndex++;
            }
        }

        // 2) 执行（本地模拟）。QuantumExecutionEnvironment 为抽象，未来可替换为云端执行环境。
        QuantumExecutionEnvironment qee = new SimpleQuantumExecutionEnvironment();
        Result result;
        try {
            result = qee.runProgram(program);
        } catch (RuntimeException e) {
            throw new CircuitException("Simulation failed: " + e.getMessage(), e);
        }

        // 3) 提取态矢量与概率
        Complex[] sv = result.getProbability();
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

        // 4) 每个量子比特处于 |1> 的概率
        Qubit[] qubitsArr = result.getQubits();
        List<Double> qubitProbs = new ArrayList<>(qubits);
        for (Qubit q : qubitsArr) {
            qubitProbs.add(round(q.getProbability()));
        }

        // 5) 采样测量（shots 次），生成经典比特串直方图
        Map<String, Integer> counts = new LinkedHashMap<>();
        String sample = null;
        for (int s = 0; s < shots; s++) {
            result.measureSystem();
            int m = result.getMeasuredProbability();
            if (s == 0) {
                sample = toBits(m, qubits);
            }
            String key = toBits(m, qubits);
            counts.merge(key, 1, Integer::sum);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("qubits", qubits);
        out.put("mode", AppConfig.mode());
        out.put("stateVector", stateVector);
        out.put("probabilities", probabilities);
        out.put("qubitProbabilities", qubitProbs);
        out.put("shots", shots);
        out.put("counts", counts);
        if (sample != null) {
            out.put("sample", sample);
        }
        return out;
    }

    // ------------------------------------------------------------------
    // 工具方法
    // ------------------------------------------------------------------

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
        throw new CircuitException("'targets' must be an array of qubit indices");
    }

    /** 保留 6 位小数并清理 -0.0。 */
    private static double round(double v) {
        if (Math.abs(v) < 1e-9) {
            return 0.0;
        }
        return Math.round(v * 1_000_000.0) / 1_000_000.0;
    }
}
