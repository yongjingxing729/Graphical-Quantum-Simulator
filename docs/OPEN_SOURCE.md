# 开源合规说明（Open Source Compliance）

本项目**严格遵守开源规则**，使用并内置了第三方开源项目
[Strange](https://github.com/redfx-quantum/strange)。

## 1. 使用的第三方开源项目

| 项目 | 许可证 | 版权 | 用途 | 存放位置 |
| --- | --- | --- | --- | --- |
| Strange | BSD 3-Clause | Copyright (c) 2018, 2023, Johan Vos | 量子态矢量模拟内核 | `vendor/strange/`（只读） |

## 2. 合规措施

本项目满足 BSD-3-Clause 许可证的三项要求：

1. **保留版权声明与条件列表、免责声明**：
   - 上游源码 `vendor/strange/` 内保留其原始 `LICENSE`；
   - 根目录 `THIRD_PARTY_NOTICES.md` 完整转载 BSD-3-Clause 全文与版权声明。
2. **二进制/再分发时再现上述声明**：
   - `NOTICE`、`THIRD_PARTY_NOTICES.md` 随项目与 Docker 镜像一并分发
     （`Dockerfile` 中显式 COPY 到镜像内）。
3. **不使用版权方名称做背书**：
   - 本项目未使用「Johan Vos」「redfx-quantum」等名义为项目背书，仅作事实性署名。

此外：

- 前端页脚、后端启动横幅、README 均明确标注上游来源与许可证。
- `strange.version` 记录锁定的上游 commit/version，保证可追溯。

## 3. 本项目代码的许可证

本项目**新增**代码（`backend/`、`frontend/`、`scripts/`、`docker/`、`docs/`）采用
**MIT License**（见根目录 `LICENSE`）。

> MIT 与 BSD-3-Clause 均为宽松许可证，二者可兼容：本项目可以在遵守 BSD-3-Clause
> 对 Strange 的义务的同时，以 MIT 发布自身代码。

## 4. 上游代码的处理边界

- 上游源码从官方仓库克隆后剥离 `.git`，以普通文件形式内置到 `vendor/strange/`，**未做任何修改**；通过 `scripts/update_upstream.py` 更新。
- 本项目后端**仅调用 Strange 的公开 API**（`Program/Step/Gate/Result/各门公开构造器`），
  不复制、不重写其模拟算法。
- 若上游更新，请按 README「手动更新上游代码」一节同步 `strange.version`、
  `NOTICE`、`THIRD_PARTY_NOTICES.md`。

## 5. 参考文献

- Strange 仓库：<https://github.com/redfx-quantum/strange>
- BSD 3-Clause 许可证文本：<https://opensource.org/license/bsd-3-clause>
- MIT 许可证文本：<https://opensource.org/license/mit>
