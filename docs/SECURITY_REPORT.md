# 安全加固技术报告（Security Hardening Report）

- 项目：Graphical Quantum Simulator
- 范围：前端（HTML/CSS/JS）、后端（Java / JDK HttpServer）、Docker 镜像
- 方法：OWASP Top 10 视角的人工代码审查 + 修复 + 自动化回归测试
- 日期：2026-11-18

> 说明：本项目**无登录/鉴权**，且前端与后端同源部署（前端由后端托管），因此威胁模型是：
> 「无凭据的公共/半公共网络环境下，防御恶意请求、注入、资源滥用与信息泄露」，
> 同时保证**本地运行零用户操作 Bug**。

---

## 1. 威胁模型与边界

| 资产 | 风险 |
| --- | --- |
| 计算资源（CPU/内存） | 恶意/超大请求导致 DoS（OOM、栈溢出、CPU 打满） |
| 数据完整性 | 注入（XSS）、数值溢出导致态矢量/结果被污染 |
| 信息 | 500 错误泄露内部类名/路径/异常消息 |
| 部署 | 容器以 root 运行、跨域通配符 `*` 扩大攻击面 |

**明确不在本次范围（已评估为低风险/需外部依赖）**：鉴权与用户系统（项目本就无登录）、
CSRF（无会话/无状态，且已收窄 CORS）、HTTPS（由反向代理/TLS 终止层负责，见 §5）。

---

## 2. 发现与修复

| # | 严重度 | 问题 | 修复 |
| --- | --- | --- | --- |
| 1 | 高 | 请求体无大小限制，超大 JSON 可致 OOM | `WebServer` 限读 `MAX_BODY_BYTES`(8MB)，超限返回 413 |
| 2 | 高 | JSON 解析无深度限制，深嵌套可致 `StackOverflowError` | `Json` 增加嵌套深度上限 `MAX_DEPTH=200` |
| 3 | 中 | 云端 CORS 用 `Access-Control-Allow-Origin: *` | 改为仅当显式配置 `QSIM_CORS_ORIGIN` 时按该来源开启，默认同源不跨域 |
| 4 | 中 | 缺安全响应头 | 增加 `X-Frame-Options: DENY`、`Content-Security-Policy`、`Referrer-Policy: no-referrer`（`nosniff` 已存在） |
| 5 | 中 | 500 错误把 `e.getMessage()` 原样返回，泄露内部信息 | 改为服务端 `printStackTrace()` + 客户端统一返回「服务器内部错误」 |
| 6 | 中 | 单请求 CPU DoS：`shots × 2^qubits` 无联合上限 | 增加 `MAX_SHOT_WORK=200M` 校验，超限返回友好错误 |
| 7 | 中 | 数值溢出：`1e400`→Infinity、超大角度→NaN 污染结果 | `Json` 拒绝非有限数；`CircuitRunner` 校验 `|angle| ≤ MAX_ANGLE` |
| 8 | 低 | 前端 XSS：上游版本号等服务器返回串未转义进 innerHTML | `loadUpstream` 统一 `escapeHtml` |
| 9 | 低 | HttpServer 未设线程池，慢请求串行阻塞 | `setExecutor` 固定线程池(8) + 守护线程 |
| 10 | 低 | 容器以 root 运行 | `Dockerfile` / `Dockerfile.local` 增加 `useradd` + `USER appuser` |

### 关键代码位置

- `backend/src/com/gqs/AppConfig.java`：`MAX_BODY_BYTES`、`MAX_SHOT_WORK`、`MAX_ANGLE`、`corsOrigin()`
- `backend/src/com/gqs/json/Json.java`：`MAX_DEPTH`、`enter()/leave()`、非有限数拒绝
- `backend/src/com/gqs/server/WebServer.java`：线程池、限流读、安全头、CORS 收窄、通用错误、`Cache-Control: no-store`
- `backend/src/com/gqs/circuit/CircuitRunner.java`：采样工作量校验、角度校验
- `frontend/js/app.js`：`loadUpstream` 转义
- `Dockerfile` / `Dockerfile.local`：非 root 运行

---

## 3. 已验证的安全边界（无需改动）

- **路径穿越**：静态资源 `frontendRoot.resolve(rel).normalize()` + `target.startsWith(frontendRoot)`（Path 逐段判断）已能抵御 `../` 与 URL 编码 `%2e%2e`（`URI.getPath()` 已解码）。
- **SSRF**：`UpstreamChecker` 仅访问两个**硬编码**可信 URL（GitHub API / Maven Central），无用户可控地址；且有 3s 连接 / 4s 请求超时。
- **注入**：后端只用自研 JSON 解析器，不拼接 SQL/命令；`scripts/update_upstream.py` 属开发者本地脚本，不对外暴露。
- **XSS（其余面）**：结果渲染 `toBits/fmt/ketOf` 均为受控输出；错误提示走 `escapeHtml`。

---

## 4. 回归验证

- Java 单元测试：**61 通过 / 0 失败**（新增 4 个安全用例：深嵌套拒绝、`1e400` 拒绝、采样工作量上限、超大角度拒绝）。
- Python 单元测试：4 通过。
- 前端 `node --check`：通过。
- 手工验证：10001 步返回 400；正常 Bell 态 / 输入态结果正确。

---

## 5. 上线/云端部署建议（残余风险与后续）

1. **TLS**：生产环境务必在前置反向代理（Nginx/Caddy）终止 HTTPS，内网转发到 `127.0.0.1:8080`。
2. **限流**：无鉴权场景建议在网关层加 IP 级限流（如 Nginx `limit_req`），进一步抑制 DoS。
3. **可选鉴权**：若未来需要公开多用户，建议加反向代理层的基础认证或 JWT，而非应用层自研。
4. **依赖与镜像**：定期 `python scripts/check_upstream.py` 检查上游 Strange；镜像以非 root + 仅 JRE 运行。
5. **CORS**：确需前后端分离时，通过 `QSIM_CORS_ORIGIN=https://你的域名` 精确开启，勿用 `*`。
