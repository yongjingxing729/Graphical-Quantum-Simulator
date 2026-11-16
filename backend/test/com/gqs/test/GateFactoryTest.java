package com.gqs.test;

import com.gqs.circuit.CircuitException;
import com.gqs.circuit.GateFactory;
import org.redfx.strange.Gate;
import org.redfx.strange.gate.Cnot;
import org.redfx.strange.gate.Cz;
import org.redfx.strange.gate.Hadamard;
import org.redfx.strange.gate.Identity;
import org.redfx.strange.gate.Measurement;
import org.redfx.strange.gate.R;
import org.redfx.strange.gate.RotationX;
import org.redfx.strange.gate.RotationY;
import org.redfx.strange.gate.RotationZ;
import org.redfx.strange.gate.Swap;
import org.redfx.strange.gate.Toffoli;
import org.redfx.strange.gate.X;
import org.redfx.strange.gate.Y;
import org.redfx.strange.gate.Z;

/** GateFactory 单元测试：验证「前端门描述 → Strange 公开构造器」的映射正确。 */
public final class GateFactoryTest {

    private GateFactoryTest() {
    }

    public static void runAll() {
        TestRunner.test("H maps to Hadamard", () -> {
            Gate g = GateFactory.create("H", new int[] {0}, 0);
            TestRunner.assertTrue("instanceof", g instanceof Hadamard);
            TestRunner.assertEquals("qubit", 0, g.getMainQubitIndex());
            TestRunner.assertEquals("caption", "H", g.getCaption());
        });

        TestRunner.test("X/Y/Z/I", () -> {
            TestRunner.assertTrue("X", GateFactory.create("X", new int[] {1}, 0) instanceof X);
            TestRunner.assertTrue("Y", GateFactory.create("Y", new int[] {1}, 0) instanceof Y);
            TestRunner.assertTrue("Z", GateFactory.create("Z", new int[] {1}, 0) instanceof Z);
            TestRunner.assertTrue("I", GateFactory.create("I", new int[] {1}, 0) instanceof Identity);
        });

        TestRunner.test("S/T/P map to phase gate R", () -> {
            TestRunner.assertTrue("S", GateFactory.create("S", new int[] {0}, 0) instanceof R);
            TestRunner.assertTrue("T", GateFactory.create("T", new int[] {0}, 0) instanceof R);
            TestRunner.assertTrue("P", GateFactory.create("P", new int[] {0}, 1.0) instanceof R);
        });

        TestRunner.test("RX/RY/RZ", () -> {
            TestRunner.assertTrue("RX", GateFactory.create("RX", new int[] {0}, 1.0) instanceof RotationX);
            TestRunner.assertTrue("RY", GateFactory.create("RY", new int[] {0}, 1.0) instanceof RotationY);
            TestRunner.assertTrue("RZ", GateFactory.create("RZ", new int[] {0}, 1.0) instanceof RotationZ);
        });

        TestRunner.test("two-qubit gates", () -> {
            TestRunner.assertTrue("CNOT", GateFactory.create("CNOT", new int[] {0, 1}, 0) instanceof Cnot);
            TestRunner.assertTrue("CZ", GateFactory.create("CZ", new int[] {0, 1}, 0) instanceof Cz);
            TestRunner.assertTrue("SWAP", GateFactory.create("SWAP", new int[] {0, 1}, 0) instanceof Swap);
        });

        TestRunner.test("three-qubit Toffoli", () -> {
            TestRunner.assertTrue("TOFFOLI",
                GateFactory.create("TOFFOLI", new int[] {0, 1, 2}, 0) instanceof Toffoli);
        });

        TestRunner.test("measurement aliases", () -> {
            TestRunner.assertTrue("M", GateFactory.create("M", new int[] {0}, 0) instanceof Measurement);
            TestRunner.assertTrue("MEASURE",
                GateFactory.create("MEASURE", new int[] {0}, 0) instanceof Measurement);
        });

        TestRunner.test("type is case-insensitive", () -> {
            TestRunner.assertTrue("lower h", GateFactory.create("h", new int[] {0}, 0) instanceof Hadamard);
            TestRunner.assertTrue("cnot", GateFactory.create("cnot", new int[] {0, 1}, 0) instanceof Cnot);
        });

        TestRunner.test("arity mismatch throws", () -> {
            TestRunner.assertThrows("CNOT 1 target", CircuitException.class,
                () -> GateFactory.create("CNOT", new int[] {0}, 0));
            TestRunner.assertThrows("H 2 targets", CircuitException.class,
                () -> GateFactory.create("H", new int[] {0, 1}, 0));
        });

        TestRunner.test("unsupported type throws", () -> {
            TestRunner.assertThrows("FOO", CircuitException.class,
                () -> GateFactory.create("FOO", new int[] {0}, 0));
            TestRunner.assertThrows("null type", CircuitException.class,
                () -> GateFactory.create(null, new int[] {0}, 0));
        });
    }
}
