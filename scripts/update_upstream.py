#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
一键更新上游 Strange 源码到 vendor/strange（仅用 Python 标准库）。

用法：
    python scripts/update_upstream.py

它做的事：
    1. 把官方仓库最新代码克隆到临时目录（不污染现有 vendor）；
    2. 删除临时克隆的 .git（保持 vendor 为普通文件，便于本项目上传/分发）；
    3. 用新代码替换 vendor/strange；
    4. 把新 HEAD commit 写回 strange.version（version 字段保持不变，需人工核对）。

设计要点：
    - 先克隆到临时目录、成功后再替换，克隆失败不会破坏现有 vendor；
    - 不会修改上游任何源码（只做复制），符合「不得修改模拟代码」的要求。
"""

import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parent.parent
VENDOR = PROJECT_ROOT / "vendor" / "strange"
VERSION_FILE = PROJECT_ROOT / "strange.version"
UPSTREAM = "https://github.com/redfx-quantum/strange.git"


def _setup_stdout():
    """强制 UTF-8 输出，避免 Windows GBK 控制台编码问题。"""
    for stream in (sys.stdout, sys.stderr):
        try:
            stream.reconfigure(encoding="utf-8", errors="replace")
        except Exception:
            pass


def run(cmd, cwd=None):
    return subprocess.run(cmd, cwd=cwd, capture_output=True, text=True)


def main() -> int:
    _setup_stdout()
    print("=" * 60)
    print(" 更新上游 Strange 源码 -> vendor/strange")
    print("=" * 60)

    if shutil.which("git") is None:
        print("[x] 未找到 git，请先安装 git。")
        return 1

    tmp = Path(tempfile.mkdtemp(prefix="strange-update-"))
    clone_dir = tmp / "strange"
    try:
        print(f"克隆 {UPSTREAM} ...")
        r = run(["git", "clone", "--depth", "1", UPSTREAM, str(clone_dir)])
        if r.returncode != 0:
            print("[x] git clone 失败：")
            print(r.stderr.strip())
            return 1

        sha = run(["git", "rev-parse", "HEAD"], cwd=clone_dir).stdout.strip()
        if not sha:
            print("[x] 无法读取上游 HEAD commit。")
            return 1

        # 安全替换：先备份旧目录，失败可回滚
        backup = tmp / "old-vendor"
        if VENDOR.exists():
            shutil.move(str(VENDOR), str(backup))
        try:
            shutil.copytree(clone_dir, VENDOR, ignore=shutil.ignore_patterns(".git"))
        except Exception as e:
            print(f"[x] 复制失败：{e}，正在回滚...")
            if VENDOR.exists():
                shutil.rmtree(VENDOR, ignore_errors=True)
            if backup.exists():
                shutil.move(str(backup), str(VENDOR))
            return 1

        # 更新 strange.version 的 commit 行
        if VERSION_FILE.exists():
            lines = VERSION_FILE.read_text(encoding="utf-8").splitlines()
            out = []
            for line in lines:
                if line.strip().startswith("commit="):
                    out.append("commit=" + sha)
                else:
                    out.append(line)
            VERSION_FILE.write_text("\n".join(out) + "\n", encoding="utf-8")
            print(f"[OK] 已更新 strange.version: commit={sha}")
        else:
            print("[!] 未找到 strange.version，请手动记录 commit=" + sha)

        print()
        print("下一步（务必执行）：")
        print("  1) 核对后端适配层（GateFactory.java / CircuitRunner.java）是否仍兼容")
        print("  2) 如发布版本变化，更新 strange.version 的 version 与 NOTICE/THIRD_PARTY_NOTICES.md")
        print("  3) 重新构建并做回归测试：")
        print("       ./build.sh && ./run.sh        # Windows: .\\build.ps1 / .\\run.ps1")
        return 0
    finally:
        shutil.rmtree(tmp, ignore_errors=True)


if __name__ == "__main__":
    sys.exit(main())
