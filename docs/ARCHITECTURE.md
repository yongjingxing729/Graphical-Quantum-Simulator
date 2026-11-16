# 架构说明（Architecture）

## 1. 总览

本项目是一个「可视化量子线路编排 + 本地模拟」的三层应用：

```
┌────────────┐   HTTP(JSON)   ┌─────────────────────────────┐   公开API   ┌──────────────────┐
│  前端浏览器  │ ─────────────▶ │  Java 后端（JDK HttpServer） │ ──────────▶ │  Strange 库(只读) │
│ (HTML/CSS/JS)│ ◀───────────── │  - 线路数据组装             │ ◀────────── │  (vendor/strange) │
└────────────┘   结果/态矢量    │  - 模式切换                 │   态矢量     └──────────────────┘
                              │  - 上游更新检查             │
                              └─────────────────────────────┘
```

## 2. 分层职责

### 前端（frontend/）

- 原生 HTML/CSS/JS，无构建步骤、无框架、无运行时依赖。
- 维护线路模型 `columns[时间步] = [ {type, targets, angle?} ]`。
  - **一列 = Strange 的一个 `Step`**：同一列内不同比特可并行施加门；
  - 同一列内同一比特只能有一个门（与 Strange `Step.verifyUnique` 约束一致）。
- 门面板、线路画布、结果渲染（直方图 / 态矢量表 / 各比特概率）。

### 后端（backend/）

| 类 | 职责 |
| --- | --- |
| `Main` | 入口，打印模式/上游提醒，启动服务器 |
| `AppConfig` | **单一变量 `RUN_MODE`** 控制本地/云端；环境变量可覆盖 |
| `server.WebServer` | JDK `HttpServer` 路由、静态资源托管、CORS（云端模式） |
| `circuit.GateFactory` | 前端门描述 → Strange 公开构造器（`new Hadamard(i)` 等） |
| `circuit.CircuitRunner` | 构建 `Program` → 执行 → 提取态矢量/概率/测量 |
| `upstream.UpstreamChecker` | 读取 `strange.version`，尽力而为查询上游更新 |
| `json.Json` | 极简 JSON 解析/序列化（避免第三方依赖） |

### 量子内核（vendor/strange/）

- 上游 Strange 源码，**只读、未修改**。
- 后端仅使用以下公开 API：
  - `new Program(n)`、`program.addStep(step)`
  - `new Step()`、`step.addGate(gate)`
  - 各 `Gate` 公开构造器（`X/Y/Z/Hadamard/Identity/R/RotationX/Y/Z/Cnot/Cz/Swap/Toffoli/Measurement`）
  - `SimpleQuantumExecutionEnvironment().runProgram(program)` → `Result`
  - `Result.getProbability()`（态矢量 `Complex[]`）、`Result.getQubits()`（各比特概率）、
    `Result.measureSystem()` / `getMeasuredProbability()`（采样测量）
  - `Complex.r` / `Complex.i`（振幅实部/虚部）、`abssqr()`（概率）

## 3. 关键设计决策

### 3.1 本地/云端模式（需求 1）

`AppConfig.RUN_MODE` 是唯一需要手动修改的开关：

- `"local"` → 绑定 `127.0.0.1`（仅本机）；
- `"cloud"` → 绑定 `0.0.0.0`、开启 CORS（前端可分离部署）。

环境变量 `QSIM_MODE` 可覆盖该常量，供 Docker/CI 注入。两者最终都汇聚到
`AppConfig.mode()`，逻辑单一、无分支散落。

### 3.2 不修改上游核心逻辑（需求 5）

- 上游源码从官方仓库克隆后剥离 `.git`，以普通文件形式内置到 `vendor/strange/`，与主项目代码物理隔离；更新由 `scripts/update_upstream.py` 完成。
- 后端**从不 import 上游内部实现类**（如 `Computations`），只使用 `Program/Step/Gate/Result`
  等公开契约与各门的公开构造器。
- 编译脚本 `build.sh` 将 `vendor/strange` 与 `backend` 一并编译，但**只读引用**上游源码。

### 3.3 手动更新机制（需求 2）

- `strange.version` 记录锁定 commit/version（单一事实来源）。
- `scripts/check_upstream.py` 对比 GitHub 最新 commit 与 Maven 最新版本，输出更新步骤。
- 后端 `/api/upstream` 与启动日志、前端横幅共同构成「开发者提醒」。

### 3.4 无第三方依赖的构建

后端仅依赖 JDK 内置 `HttpServer` + 自写极简 JSON，避免 Maven/Gradle 与外部 jar。
这带来两点好处：**开箱即跑**（无需下载依赖）、**可审计**（无隐藏依赖）。

## 4. 数据流（一次模拟）

1. 前端把 `columns` 序列化为 `{qubits, shots, steps:[{gates:[{type,targets,angle}]}]}`。
2. 后端 `CircuitRunner` 逐列构建 `Step`，逐门调用 `GateFactory.create` 生成 Strange `Gate`。
3. `program.addStep(step)`（若测量后再叠加会被 Strange 拒绝，后端捕获并返回友好错误）。
4. `SimpleQuantumExecutionEnvironment.runProgram(program)` 计算态矢量。
5. 提取：`getProbability()` → 态矢量；`getQubits()` → 各比特 `|1⟩` 概率；
   循环 `measureSystem()` `shots` 次 → 采样直方图。
6. 组装 JSON 返回前端渲染。

## 5. 已知边界（MVP）

- 状态矢量维度为 `2^n`，前端与后端均限制 `n ≤ 12` 以避免指数级内存爆炸。
- Strange 的测量门不「坍缩」态矢量（本地模拟保留全态矢量），采样由 `measureSystem()` 完成，
  这是上游库的既定行为，本项目如实呈现。
- 云端模式仍使用本地 `SimpleQuantumExecutionEnvironment` 做模拟；真实量子云后端可通过
  替换 `QuantumExecutionEnvironment` 实现接入（接口已抽象）。
