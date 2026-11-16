#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
上游 Strange 仓库更新检查 / 开发者提醒脚本（仅用 Python 标准库，无第三方依赖）。

用法：
    python scripts/check_upstream.py

功能：
    1. 读取项目根目录 strange.version 中锁定的上游 commit 与版本号；
    2. 查询 GitHub API 获取上游 main 分支最新 commit；
    3. 查询 Maven Central 获取最新发布版本；
    4. 对比并打印报告；若上游存在更新，则以退出码 1 提醒开发者。

说明：
    本脚本只做「检查与提醒」，不会自动改动 vendor/strange 或 strange.version；
    更新动作由开发者手动完成（见 README「手动更新上游代码」一节）。
"""

import json
import sys
import urllib.error
import urllib.request
from pathlib import Path

REPO = "https://github.com/redfx-quantum/strange"
GITHUB_API = "https://api.github.com/repos/redfx-quantum/strange/commits/main"
MAVEN_API = (
    "https://search.maven.org/solrsearch/select?q=g:org.redfx+AND+a:strange&rows=1&wt=json"
)


def _setup_stdout():
    """强制 UTF-8 输出，避免 Windows GBK 控制台编码问题；不可编码字符用 ? 替换。"""
    for stream in (sys.stdout, sys.stderr):
        try:
            stream.reconfigure(encoding="utf-8", errors="replace")
        except Exception:
            pass


def project_root() -> Path:
    """scripts/ 目录的上级目录即项目根目录。"""
    return Path(__file__).resolve().parent.parent


def read_pinned(root: Path):
    """读取 strange.version 中的 repo/commit/version/license。"""
    data = {"repo": REPO, "commit": "", "version": "", "license": "BSD-3-Clause"}
    f = root / "strange.version"
    if not f.exists():
        print(f"[警告] 未找到 {f}，将只显示远端信息。", file=sys.stderr)
        return data
    for line in f.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        if "=" in line:
            k, v = line.split("=", 1)
            k, v = k.strip(), v.strip()
            if k in data:
                data[k] = v
    return data


def http_get_json(url: str, timeout: float = 10.0):
    """GET 并解析 JSON；网络异常/非 200 时返回 None。"""
    req = urllib.request.Request(url, headers={"User-Agent": "gqs-upstream-checker"})
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            if resp.status != 200:
                return None
            return json.loads(resp.read().decode("utf-8"))
    except (urllib.error.URLError, OSError, ValueError, TimeoutError):
        return None


def fetch_latest_commit():
    data = http_get_json(GITHUB_API)
    if isinstance(data, dict):
        sha = data.get("sha")
        if sha:
            return str(sha).strip()
    return None


def fetch_latest_version():
    data = http_get_json(MAVEN_API)
    try:
        docs = data["response"]["docs"]
        if docs:
            return str(docs[0]["latestVersion"])
    except (KeyError, IndexError, TypeError):
        pass
    return None


def main() -> int:
    _setup_stdout()
    root = project_root()
    pinned = read_pinned(root)

    print("=" * 66)
    print(" 上游开源仓库更新检查  (Strange / BSD-3-Clause)")
    print("=" * 66)
    print(f" 仓库        : {REPO}")
    print(f" 锁定 commit : {pinned['commit'] or '(未记录)'}")
    print(f" 锁定版本    : {pinned['version'] or '(未记录)'}")
    print("-" * 66)

    latest_commit = fetch_latest_commit()
    latest_version = fetch_latest_version()

    commit_status = "未知（无法访问 GitHub）"
    version_status = "未知（无法访问 Maven Central）"
    has_update = False

    if latest_commit:
        if pinned["commit"] and latest_commit != pinned["commit"]:
            commit_status = f"有更新！最新 {latest_commit}"
            has_update = True
        else:
            commit_status = f"一致（{latest_commit}）"

    if latest_version:
        if pinned["version"] and latest_version != pinned["version"]:
            version_status = f"有更新！最新 {latest_version}"
            has_update = True
        else:
            version_status = f"一致（{latest_version}）"

    print(f" GitHub main : {commit_status}")
    print(f" Maven 版本  : {version_status}")
    print("-" * 66)

    if has_update:
        print(" [!] 检测到上游存在新变更，请开发者按以下步骤手动更新：")
        print("    1) 更新上游源码:  python scripts/update_upstream.py")
        print("    2) 核对差异，确认本项目的 API 调用是否受影响（后端只调用公开 API）")
        print("    3) 更新 strange.version 中的 commit / version")
        print("    4) 同步 NOTICE / THIRD_PARTY_NOTICES.md 中的版权与版本信息")
        print("    5) 重新构建并测试:  ./build.sh (或 build.ps1) && ./run.sh")
        print("=" * 66)
        return 1

    print(" [OK] 上游已是最新，无需更新。")
    print("    如需手动刷新 vendor/strange 源码，可运行:")
    print("    python scripts/update_upstream.py")
    print("=" * 66)
    return 0


if __name__ == "__main__":
    sys.exit(main())
