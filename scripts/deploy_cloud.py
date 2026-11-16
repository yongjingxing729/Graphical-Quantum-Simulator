#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
云端部署辅助脚本（仅用 Python 标准库）。

用法：
    python scripts/deploy_cloud.py check      检查云部署前置条件（Java / Docker）
    python scripts/deploy_cloud.py info       打印当前本地/云端配置
    python scripts/deploy_cloud.py docker     构建并运行 Docker 容器（云端模式）
    python scripts/deploy_cloud.py help       显示帮助

云端运行模式的切换方式（详见 backend/src/com/gqs/AppConfig.java）：
    - 方式一：修改 AppConfig.RUN_MODE = "cloud"（代码中的单一变量）
    - 方式二：设置环境变量 QSIM_MODE=cloud（容器/编排平台更常用）
"""

import os
import shutil
import subprocess
import sys
from pathlib import Path

def _setup_stdout():
    """强制 UTF-8 输出，避免 Windows GBK 控制台编码问题；不可编码字符用 ? 替换。"""
    for stream in (sys.stdout, sys.stderr):
        try:
            stream.reconfigure(encoding="utf-8", errors="replace")
        except Exception:
            pass


PROJECT_ROOT = Path(__file__).resolve().parent.parent
IMAGE_NAME = "graphical-quantum-simulator"
CONTAINER_NAME = "gqs"


def run(cmd, **kwargs):
    print("$", " ".join(cmd))
    return subprocess.call(cmd, **kwargs)


def check():
    """检查前置条件。"""
    ok = True
    java = shutil.which("java")
    javac = shutil.which("javac")
    docker = shutil.which("docker")

    print("云端部署前置条件检查：")
    print(f"  java  : {'[OK] ' + java if java else '[x] 未找到（需要 JDK 17+）'}")
    print(f"  javac : {'[OK] ' + javac if javac else '[x] 未找到（需要 JDK 17+）'}")
    print(f"  docker: {'[OK] ' + docker if docker else '[x] 未找到（可选，用于容器化部署）'}")

    if java and javac:
        # 检查版本是否 >= 11
        try:
            out = subprocess.check_output([javac, "-version"], stderr=subprocess.STDOUT, text=True)
            print(f"        版本: {out.strip()}")
        except Exception:
            pass

    ok = bool(java and javac)
    if not ok:
        print("\n请先安装 JDK 17+（或 11+），再执行本脚本。")
    else:
        print("\n[OK] 本地构建环境就绪。")
    return 0 if ok else 1


def info():
    """打印当前配置。"""
    app_cfg = PROJECT_ROOT / "backend" / "src" / "com" / "gqs" / "AppConfig.java"
    run_mode = None
    if app_cfg.exists():
        for line in app_cfg.read_text(encoding="utf-8").splitlines():
            if "RUN_MODE" in line and "=" in line:
                val = line.split("=", 1)[1].split(";")[0].strip().strip('"')
                run_mode = val
                break

    env_mode = os.environ.get("QSIM_MODE")
    effective = (env_mode or run_mode or "local").lower()
    host = "0.0.0.0" if effective == "cloud" else "127.0.0.1"
    port = os.environ.get("QSIM_PORT", "8080")

    print("当前运行配置：")
    print(f"  AppConfig.RUN_MODE = {run_mode!r}  (代码中的单一变量)")
    print(f"  环境变量 QSIM_MODE = {env_mode!r}")
    print(f"  生效模式           = {effective}")
    print(f"  监听地址           = {host}:{port}")
    print()
    print("切换云端模式的方法之一（不改代码）：")
    print('  $env:QSIM_MODE="cloud"  (Windows PowerShell)')
    print('  export QSIM_MODE=cloud  (Linux / macOS)')
    return 0


def docker_build_and_run():
    """构建并运行 Docker 容器（云端模式）。"""
    if not shutil.which("docker"):
        print("[x] 未找到 docker，请先安装 Docker。", file=sys.stderr)
        return 1

    print("构建 Docker 镜像...")
    if run(["docker", "build", "-t", IMAGE_NAME, str(PROJECT_ROOT)]) != 0:
        return 1

    print("停止并移除旧容器（若存在）...")
    run(["docker", "rm", "-f", CONTAINER_NAME])

    print("启动容器（云端模式，绑定 0.0.0.0:8080）...")
    cmd = [
        "docker", "run", "-d", "--name", CONTAINER_NAME,
        "-p", "8080:8080",
        "-e", "QSIM_MODE=cloud",
        "-e", "QSIM_PORT=8080",
        IMAGE_NAME,
    ]
    if run(cmd) != 0:
        return 1

    print()
    print("[OK] 容器已启动。访问 http://<服务器公网IP>:8080")
    print("  查看日志: docker logs -f " + CONTAINER_NAME)
    print("  停止容器: docker stop " + CONTAINER_NAME)
    return 0


def main():
    _setup_stdout()
    cmd = sys.argv[1] if len(sys.argv) > 1 else "help"
    if cmd == "check":
        return check()
    if cmd == "info":
        return info()
    if cmd == "docker":
        return docker_build_and_run()
    print(__doc__)
    return 0


if __name__ == "__main__":
    sys.exit(main())
