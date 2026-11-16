package com.gqs.circuit;

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

/**
 * 把前端传来的「门」描述转换为 Strange 上游库的 {@link Gate} 实例。
 *
 * <p>本类<strong>只调用上游 Strange 的公开构造器</strong>，不复制、不修改其模拟逻辑。</p>
 */
public final class GateFactory {

    private GateFactory() {
    }

    /**
     * 根据门类型、目标比特、角度参数创建 Strange 门实例。
     *
     * @param type    门类型（大小写不敏感）：H/X/Y/Z/I/S/T/P/RX/RY/RZ/CNOT/CZ/SWAP/TOFFOLI/M
     * @param targets 目标量子比特下标数组
     * @param angle   旋转角度（弧度），仅 RX/RY/RZ/P 使用
     */
    public static Gate create(String type, int[] targets, double angle) {
        if (type == null) {
            throw new CircuitException("Gate type is missing");
        }
        switch (type.toUpperCase()) {
            case "X":      require(targets, 1, type); return new X(targets[0]);
            case "Y":      require(targets, 1, type); return new Y(targets[0]);
            case "Z":      require(targets, 1, type); return new Z(targets[0]);
            case "H":      require(targets, 1, type); return new Hadamard(targets[0]);
            case "I":      require(targets, 1, type); return new Identity(targets[0]);
            case "S":      require(targets, 1, type); return new R(Math.PI / 2, targets[0]);   // S = 相位门 R(π/2)
            case "T":      require(targets, 1, type); return new R(Math.PI / 4, targets[0]);   // T = 相位门 R(π/4)
            case "P":      require(targets, 1, type); return new R(angle, targets[0]);         // 通用相位门 R(θ)
            case "RX":     require(targets, 1, type); return new RotationX(angle, targets[0]);
            case "RY":     require(targets, 1, type); return new RotationY(angle, targets[0]);
            case "RZ":     require(targets, 1, type); return new RotationZ(angle, targets[0]);
            case "CNOT":   require(targets, 2, type); return new Cnot(targets[0], targets[1]);
            case "CZ":     require(targets, 2, type); return new Cz(targets[0], targets[1]);
            case "SWAP":   require(targets, 2, type); return new Swap(targets[0], targets[1]);
            case "TOFFOLI":
                require(targets, 3, type); return new Toffoli(targets[0], targets[1], targets[2]);
            case "M":
            case "MEASURE":
                require(targets, 1, type); return new Measurement(targets[0]);
            default:
                throw new CircuitException("Unsupported gate type: " + type);
        }
    }

    private static void require(int[] targets, int n, String type) {
        if (targets == null || targets.length != n) {
            throw new CircuitException(
                "Gate '" + type + "' requires exactly " + n + " target qubit index(es)");
        }
    }
}
