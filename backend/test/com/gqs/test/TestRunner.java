package com.gqs.test;

import java.util.ArrayList;
import java.util.List;

/**
 * 极简测试框架（纯 JDK，无 JUnit 依赖）。
 *
 * <p>设计目标：在「零第三方依赖」的前提下，对后端核心逻辑做可读、可回归的单元测试。</p>
 *
 * <p>用法：{@code java -cp "backend/build/classes;backend/build/test-classes" com.gqs.test.TestRunner}</p>
 */
public final class TestRunner {

    private static int passed = 0;
    private static int failed = 0;
    private static String currentClass = "";
    private static final List<String> failures = new ArrayList<>();

    private TestRunner() {
    }

    public static void main(String[] args) {
        // 屏蔽上游 Strange 的 INFO 日志，保持测试输出整洁（仅测试期设置，未改动上游代码）
        java.util.logging.Logger.getLogger("org.redfx.strange.local.SimpleQuantumExecutionEnvironment")
            .setLevel(java.util.logging.Level.WARNING);

        System.out.println("==================================================");
        System.out.println(" Graphical Quantum Simulator - 后端测试套件");
        System.out.println("==================================================");

        runClass("Json", JsonTest::runAll);
        runClass("GateFactory", GateFactoryTest::runAll);
        runClass("CircuitRunner", CircuitRunnerTest::runAll);
        runClass("UpstreamChecker", UpstreamCheckerTest::runAll);

        System.out.println("--------------------------------------------------");
        System.out.printf("结果: %d passed, %d failed%n", passed, failed);
        if (failed > 0) {
            System.out.println("失败用例:");
            failures.forEach(f -> System.out.println("  - " + f));
            System.exit(1);
        }
        System.out.println("[OK] 全部通过");
    }

    private static void runClass(String name, Runnable r) {
        System.out.println();
        System.out.println("[" + name + "]");
        currentClass = name;
        r.run();
    }

    /** 运行一个测试用例；抛异常即视为失败。 */
    public static void test(String name, Runnable body) {
        try {
            body.run();
            passed++;
            System.out.println("  PASS  " + name);
        } catch (Throwable t) {
            failed++;
            String msg = (t.getMessage() == null) ? t.toString() : t.getMessage();
            failures.add(currentClass + "." + name + ": " + msg);
            System.out.println("  FAIL  " + name + "  ->  " + msg);
        }
    }

    // ------------------------------------------------------------------
    // 断言
    // ------------------------------------------------------------------

    public static void assertTrue(String msg, boolean cond) {
        if (!cond) {
            throw new AssertionError(msg);
        }
    }

    public static void assertFalse(String msg, boolean cond) {
        assertTrue(msg, !cond);
    }

    public static void assertEquals(String msg, Object expected, Object actual) {
        boolean eq = (expected == null) ? (actual == null) : expected.equals(actual);
        if (!eq) {
            throw new AssertionError(msg + " (期望=" + expected + ", 实际=" + actual + ")");
        }
    }

    public static void assertClose(String msg, double expected, double actual, double eps) {
        if (Math.abs(expected - actual) > eps) {
            throw new AssertionError(msg + " (期望≈" + expected + ", 实际=" + actual + ", 误差="
                + Math.abs(expected - actual) + " > " + eps + ")");
        }
    }

    public static void assertThrows(String msg, Class<? extends Throwable> type, Runnable body) {
        try {
            body.run();
        } catch (Throwable t) {
            if (type.isInstance(t)) {
                return;
            }
            throw new AssertionError(msg + " 抛出了意外类型: " + t.getClass().getName()
                + " / " + t.getMessage());
        }
        throw new AssertionError(msg + " 未抛出异常，期望 " + type.getSimpleName());
    }
}
