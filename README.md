# Quantum Circuit Simulator（可视化量子门组合操作模拟器）

基于开源项目 **[Strange](https://github.com/redfx-quantum/strange)**（Quantum Computing API for Java，BSD-3-Clause）构建的
**可视化量子线路编排与模拟**应用。你可以在浏览器里拖选量子门、搭建量子线路，实时查看态矢量、
概率分布与测量结果；既可在本地运行，也可一键切换到云端服务器运行。

> 本项目的量子模拟内核完全来自上游 Strange 库，**未修改其任何核心模拟代码**，后端仅调用其公开 API。

---

## 目录

- [功能特性](#功能特性)
- [技术栈](#技术栈)
- [目录结构](#目录结构)
- [快速开始（本地运行）](#快速开始本地运行)
- [本地 / 云端模式切换（单一变量）](#本地--云端模式切换单一变量)
- [手动更新上游代码与开发者提醒](#手动更新上游代码与开发者提醒)
- [开源声明](#开源声明)
- [架构说明](#架构说明)
- [云端部署](#云端部署)
- [REST API](#rest-api)
- [许可证](#许可证)

---

## 功能特性

| 需求 | 实现 |
| --- | --- |
| 1. 单一变量切换本地/云端 | `backend/src/com/gqs/AppConfig.java` 中的 `RUN_MODE`（`"local"`/`"cloud"`） |
| 2. 手动更新上游代码 + 开发者提醒 | `scripts/check_upstream.py` + 前端横幅 + 启动日志提醒 |
| 3. 遵守开源规则 | `THIRD_PARTY_NOTICES.md`、`NOTICE`、`LICENSE`，前端页脚署名 |
| 4. 使用 Java + 前端语言 + Python | 后端 Java（纯 JDK）、前端原生 HTML/CSS/JS、脚本 Python |
| 5. 不动上游核心逻辑 | 上游源码只读存放于 `vendor/strange/`，后端仅调用公开 API |
| 6. MVP 含云端部署，默认本地 | 提供 Docker/云部署能力，`RUN_MODE` 默认 `"local"` |
| 7. 暂不提交 GitHub | 本仓库本地开发，未推送远程 |

**可视化能力：**

- 量子线路画布（比特线 × 时间步列），点击放置门，支持撤销/清空。
- **单量子位门数上限 10000**（即最多 10000 列时间步）；列数多时采用虚拟滚动，只渲染可视区域。
- **列后「+」插入空列**：每列末尾有「+」，点击在该列后插入一个空列（空门），用于对齐/留白。
- **批量操作**：可一次增加 N 个量子比特、一次追加 N 个空列。
- 支持门：`H / X / Y / Z / I / S / T / P(θ) / Rx(θ) / Ry(θ) / Rz(θ) / CNOT / CZ / SWAP / Toffoli / M`。
- 多比特门（CNOT/CZ/SWAP/Toffoli）依次点击各比特线放置，自动绘制控制点（●）与受控点（⊕）。
- **自定义输入态**：每个量子比特可选 `|0⟩ / |1⟩ / |+⟩ / |−⟩ / |i⟩ / |−i⟩`，并同时可视化输入态与输出态。
- **实时错误提醒**：测量后叠加（量子测量不可逆）、比特越界、非法输入等会即时提示；致命问题会禁用「运行」按钮。
- 示例线路：Bell 态、GHZ(3)、叠加态、比特翻转。
- 结果展示：输入态与输出态概率分布、输出态复振幅表、各比特 `|1⟩` 概率、测量采样直方图（shots）。

---

## 技术栈

| 层 | 技术 | 说明 |
| --- | --- | --- |
| 后端 | Java（JDK 21+，纯 JDK 无第三方依赖） | JDK 内置 `HttpServer` 提供 REST API；仅依赖上游 Strange |
| 量子内核 | Strange（BSD-3-Clause） | 上游 `vendor/strange/` 源码，只读、未修改 |
| 前端 | 原生 HTML / CSS / JavaScript | 无构建步骤、无框架，由后端直接托管 |
| 脚本 | Python 3（仅标准库） | 上游更新提醒、云端部署辅助 |
| 部署 | Docker / Docker Compose | 云端容器化部署 |

---

## 目录结构

```
Graphical-Quantum-Simulator/
├── backend/                        # Java 后端（纯 JDK，无第三方依赖）
│   ├── src/com/gqs/
│   │   ├── Main.java               # 入口，启动服务器
│   │   ├── AppConfig.java          # ★ 单一变量 RUN_MODE（本地/云端）
│   │   ├── json/Json.java          # 极简 JSON 解析/序列化（无依赖）
│   │   ├── circuit/
│   │   │   ├── GateFactory.java    # 前端门描述 → Strange 公开构造器
│   │   │   ├── CircuitRunner.java  # 构建 Program、执行、提取结果
│   │   │   └── CircuitException.java
│   │   ├── upstream/UpstreamChecker.java # 上游更新检查（尽力而为）
│   │   └── server/WebServer.java   # HTTP 路由 + 静态资源托管
│   ├── test/com/gqs/test/          # ★ 后端测试（纯 JDK 测试框架，无 JUnit）
│   │   ├── TestRunner.java         # 测试入口 + 断言工具
│   │   ├── JsonTest.java
│   │   ├── GateFactoryTest.java
│   │   ├── CircuitRunnerTest.java  # 线路物理正确性 / 错误路径
│   │   └── UpstreamCheckerTest.java
│   └── build/                      # 编译产物（.gitignore 忽略）
├── frontend/                       # 前端（原生静态文件）
│   ├── index.html
│   ├── css/style.css
│   └── js/app.js
├── scripts/                        # Python 脚本
│   ├── check_upstream.py           # ★ 上游更新提醒
│   ├── update_upstream.py          # ★ 一键更新上游源码（重新克隆）
│   ├── deploy_cloud.py             # 云端部署辅助
│   └── tests/test_scripts.py       # ★ Python 单元测试（unittest）
├── vendor/strange/                 # ★ 上游 Strange 源码（只读，未修改）
├── docs/                           # 架构 / 部署 / 开源说明
├── strange.version                 # ★ 锁定的上游 commit/版本（供更新提醒）
├── Dockerfile                      # 云端部署镜像
├── docker-compose.yml              # 云端部署编排
├── build.ps1 / build.sh            # 编译脚本
├── run.ps1 / run.sh                # 运行脚本
├── run_tests.ps1 / run_tests.sh    # ★ 测试脚本（Java + Python）
├── LICENSE                         # 本项目代码许可证（MIT）
├── NOTICE                          # 第三方署名
└── THIRD_PARTY_NOTICES.md          # ★ 第三方开源声明
```

---

## 快速开始（本地运行）

**前置条件**：JDK 21+（本机已用 JDK 23 验证；上游 Strange 使用了 Java 21 的 `List.getLast` API）。无需 Maven/Gradle，无需 Node。

Windows（PowerShell）：

```powershell
# 在项目根目录执行
.\run.ps1
```

Linux / macOS：

```bash
./run.sh
```

首次运行会自动编译（后端 + 上游 Strange 源码）。编译成功后访问：

- 前端界面：<http://127.0.0.1:8080>
- 元信息接口：<http://127.0.0.1:8080/api/meta>

> **PowerShell 执行策略提示**：若 `.ps1` 被系统拦截，请运行
> `powershell -ExecutionPolicy Bypass -File .\run.ps1`，
> 或 `Set-ExecutionPolicy -Scope CurrentUser RemoteSigned` 后重试。

---

## 本地 Docker 运行（可选）

如果希望用 Docker 在**本机**运行（不对外开放），已提供一键脚本：

```powershell
# Windows（会自动编译 -> 构建镜像 -> 启动容器）
powershell -ExecutionPolicy Bypass -File .\docker_local.ps1
```

```bash
# Linux / macOS
./docker_local.sh
```

启动后访问：<http://127.0.0.1:8080>

脚本做了三件事：

1. 用 `javac --release 21` 把源码编译到 `prebuilt/classes21`（项目需要 **Java 21+**）；
2. 用 `Dockerfile.local` 构建一个**只含 JRE** 的轻量镜像（避免拉取体积很大的完整 JDK 镜像）；
3. 用 `docker-compose.local.yml` 启动容器，端口只映射到宿主机 `127.0.0.1`。

常用命令：

```bash
# 查看状态 / 日志
docker compose -f docker-compose.local.yml ps
docker compose -f docker-compose.local.yml logs -f

# 停止 / 重新构建
docker compose -f docker-compose.local.yml down
docker compose -f docker-compose.local.yml up -d --build
```

> 说明：容器内以 `QSIM_MODE=local` + `QSIM_BIND_HOST=0.0.0.0` 运行（容器内必须监听
> `0.0.0.0`，否则宿主机端口映射无法访问），但端口只映射到 `127.0.0.1`，因此**只有本机能访问**。
>
> 另外，标准 `Dockerfile` 是自包含的多阶段构建（拉取 `eclipse-temurin:21-jdk` 在容器内编译），
> 适合云端 / CI；本地运行时用上面的 `Dockerfile.local` 更快。

---

## 运行测试

项目自带两套测试，均**零第三方依赖**（不使用 JUnit / pytest）：

- **Java 后端测试**（`backend/test/`）：自带极简测试框架，覆盖
  JSON 解析/序列化、门工厂映射、线路执行（Bell / GHZ / X / SWAP / Toffoli / 旋转门）、
  比特序、采样直方图，以及非法输入/测量后叠加等错误路径。
- **Python 单元测试**（`scripts/tests/`）：用标准库 `unittest` 验证 `check_upstream` 的
  配置解析逻辑。

一键运行（会先自动编译主代码与测试代码）：

```powershell
# Windows
powershell -ExecutionPolicy Bypass -File .\run_tests.ps1
```

```bash
# Linux / macOS
./run_tests.sh
```

预期结果：

```
[JSON] ...
[GateFactory] ...
[CircuitRunner] ...
[UpstreamChecker] ...
结果: 47 passed, 0 failed
[OK] 全部通过
Ran 4 tests ... OK
```

> 测试只针对本项目自身的代码与桥接层，**不会修改上游 Strange**。

---

## 本地 / 云端模式切换（单一变量）

打开 `backend/src/com/gqs/AppConfig.java`：

```java
public static final String RUN_MODE = "local"; // 改为 "cloud" 即切换云端
```

| 取值 | 监听地址 | 说明 |
| --- | --- | --- |
| `"local"`（默认） | `127.0.0.1` | 仅本机访问，适合本地开发 |
| `"cloud"` | `0.0.0.0` | 所有网卡，开放跨域，适合云端服务器 |

`RUN_MODE` 即需求 1 所说的「一个变量」。改完后重新运行即可生效。

> 在 Docker / 云端编排场景下，也可以**不改代码**，通过环境变量覆盖：
> `QSIM_MODE=cloud`、`QSIM_PORT=8080`、`QSIM_FRONTEND=frontend`。
> 其中 `QSIM_BIND_HOST=0.0.0.0` 可在容器内强制监听所有网卡（Docker 端口映射需要）。
> 两种方式都指向同一个开关，环境变量优先级更高（便于 CI/CD 注入）。

---

## 手动更新上游代码与开发者提醒

本项目把上游 Strange 源码以**只读**方式内置在 `vendor/strange/`（从官方仓库克隆后剥离 `.git`，
以普通文件形式随仓库分发，保证 `git clone` 后即可编译运行、无需额外子模块初始化），
并用 `strange.version` 记录锁定的 `commit` 与 `version`。

### 开发者提醒（三种形式）

1. **启动日志**：后端启动时会打印上游锁定信息与检查命令。
2. **前端横幅**：页面顶部调用 `/api/upstream`，若有新变更会显示黄色警示横幅。
3. **命令行脚本**：主动检查上游是否有更新：

   ```bash
   python scripts/check_upstream.py
   ```

   脚本会对比 GitHub 最新 commit 与 Maven Central 最新版本；若上游有新变更，
   会打印更新步骤并以退出码 `1` 提醒（可接入 CI）。

### 手动更新步骤

```bash
# 1. 一键更新上游最新代码（重新克隆并自动写回 strange.version 的 commit）
python scripts/update_upstream.py

# 2. 核对差异：确认后端适配层（GateFactory.java / CircuitRunner.java）是否仍兼容

# 3. 如发布版本变化，同步 strange.version 的 version 与 NOTICE / THIRD_PARTY_NOTICES.md

# 4. 重新构建并做回归测试（Bell / GHZ / Rx / SWAP / Toffoli）
./build.sh && ./run.sh        # 或 Windows 下 .\build.ps1 / .\run.ps1
```

> 也可以手动更新：删除 `vendor/strange/` 后执行
> `git clone https://github.com/redfx-quantum/strange.git vendor/strange`，
> 再删除其 `.git` 并更新 `strange.version`。推荐直接使用上面的脚本。

> 注意：`vendor/strange/` 中的代码**不要修改**（需求 5）。如确需改动，请优先向上游提交 PR，
> 本项目通过「拉取上游更新」的方式吸收改进，而不是在 vendor 内打补丁。

---

## 开源声明

本项目**严格遵守开源规则**：

- 上游 Strange 的许可证为 **BSD 3-Clause License**，版权 `Copyright (c) 2018, 2023, Johan Vos`。
- 上游源码以只读方式置于 `vendor/strange/`，其原始 `LICENSE` 一并保留。
- 本项目新增代码（`backend/`、`frontend/`、`scripts/`、`docker/`、`docs/`）采用 **MIT License**（见 `LICENSE`）。
- 第三方完整声明与 BSD-3-Clause 全文见 **[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)**，署名见 **[NOTICE](NOTICE)**。
- 前端页脚、后端启动横幅均明确标注上游来源。

更详细的合规说明见 [docs/OPEN_SOURCE.md](docs/OPEN_SOURCE.md)。

---

## 架构说明

```
浏览器(前端) ──HTTP──▶ Java 后端(HttpServer) ──调用公开API──▶ Strange 库(只读)
   │                      │  ▲                                  (vendor/strange)
   │  /api/circuit/run    │  │ 态矢量/概率/测量
   ▼                      ▼  │
 线路画布 ◀──JSON 结果────┘  │
                            │ /api/upstream ──尽力而为──▶ GitHub / Maven Central
```

- **后端职责**：接收线路 JSON → 用 `GateFactory` 映射为 Strange 的 `Gate` → 组装 `Program` →
  交给 `QuantumExecutionEnvironment`（本地 `SimpleQuantumExecutionEnvironment`）执行 →
  提取态矢量/概率/测量结果返回。
- **核心逻辑边界**：所有量子态计算都在 Strange 内部完成，后端不复制、不重写任何模拟算法，
  只做「数据组装」与「结果提取」。上游代码零改动。
- **模式切换**：`AppConfig.RUN_MODE` 决定监听地址与跨域行为；`QuantumExecutionEnvironment`
  为抽象接口，未来可平滑替换为真实量子云后端。

详细设计见 [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)。

---

## 云端部署

完整的云端部署指南（Docker、云服务器、反向代理、环境变量）见
[docs/CLOUD_DEPLOYMENT.md](docs/CLOUD_DEPLOYMENT.md)。快速开始：

```bash
# 方式一：Docker Compose（推荐）
docker compose up -d --build

# 方式二：Docker
docker build -t graphical-quantum-simulator .
docker run -d -p 8080:8080 -e QSIM_MODE=cloud graphical-quantum-simulator

# 方式三：直接运行（在服务器上，设置环境变量）
export QSIM_MODE=cloud
./run.sh
```

启动后访问 `http://<服务器公网IP>:8080`。

辅助脚本：`python scripts/deploy_cloud.py check|info|docker`。

---

## REST API

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/meta` | 应用信息、模式、支持的门、上游锁定信息 |
| `GET` | `/api/upstream` | 上游更新状态（含尽力而为的在线检查） |
| `POST` | `/api/circuit/run` | 执行线路模拟 |

`POST /api/circuit/run` 请求示例（输入态 |+⟩|0⟩，施加 CNOT 制备 Bell 态）：

```json
{
  "qubits": 2,
  "shots": 1024,
  "inputs": ["+", "0"],
  "steps": [
    { "gates": [ { "type": "CNOT", "targets": [0, 1] } ] }
  ]
}
```

`inputs` 为每个量子比特的初始输入态，取值 `0 / 1 / + / - / i / -i`（省略则默认全 `0`，即 |0⟩）。

响应包含：输入态 `inputStateVector` / `inputProbabilities`、输出态 `stateVector` / `probabilities`、
`qubitProbabilities`（各比特 `|1⟩` 概率）、`counts`（采样直方图）、`sample`（一次采样结果）。

---

## 许可证

- 本项目新增代码：**MIT License**（见 [LICENSE](LICENSE)）。
- 上游 Strange：**BSD 3-Clause License**（见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) 与 `vendor/strange/LICENSE`）。

> 说明：`LICENSE` 中的版权年份/署名请按实际维护者信息调整。
