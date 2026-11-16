# 第三方开源软件声明 (Third-Party Notices)

本项目（Graphical Quantum Simulator）遵守开源规则，在构建过程中使用并内置了以下第三方
开源软件。特此声明其版权与许可证信息。

---

## 1. Strange

- **项目名称**：Strange — Quantum Computing API for Java
- **仓库地址**：<https://github.com/redfx-quantum/strange>
- **用途**：本项目的量子态矢量模拟内核（构建量子线路 `Program`、执行门运算、产生测量结果）。
- **许可证**：BSD 3-Clause License（"New" or "Revised" License）
- **版权**：Copyright (c) 2018, 2023, Johan Vos
- **本项目使用方式**：
  - 上游核心模拟代码以**只读、未修改**的方式保存在 `vendor/strange/` 目录中；
  - 本项目的 Java 后端**仅调用 Strange 的公开 API**，不修改其任何源代码；
  - 上游锁定版本记录在 `strange.version`（当前 commit `f7d62d9c5bd39856b84d3d168e45eb580402ea24`，版本 `0.1.3`）。

### 许可证全文（BSD 3-Clause License）

```
BSD 3-Clause License

Copyright (c) 2018, 2023, Johan Vos
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

* Redistributions of source code must retain the above copyright notice, this
  list of conditions and the following disclaimer.

* Redistributions in binary form must reproduce the above copyright notice,
  this list of conditions and the following disclaimer in the documentation
  and/or other materials provided with the distribution.

* Neither the name of the copyright holder nor the names of its
  contributors may be used to endorse or promote products derived from
  this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
```

---

> 本文件由项目维护者生成，用于满足开源许可证的署名与分发要求。
> 若上游项目信息发生变动，请在更新上游代码时同步更新本文件与 `NOTICE`、`strange.version`。
