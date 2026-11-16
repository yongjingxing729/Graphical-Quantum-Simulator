#!/usr/bin/env bash
# ============================================================
# 运行后端服务器（Linux / macOS / 云端容器）
# 用法：./run.sh
# 切换本地/云端模式：修改 backend/src/com/gqs/AppConfig.java 的
#   RUN_MODE，或设置环境变量 QSIM_MODE=cloud
# ============================================================
set -euo pipefail
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$PROJECT_ROOT"

CLASSES="$PROJECT_ROOT/backend/build/classes"
if [ ! -f "$CLASSES/com/gqs/Main.class" ]; then
  echo "未找到编译产物，先自动构建..."
  "$PROJECT_ROOT/build.sh"
fi

exec java -cp "$CLASSES" com.gqs.Main
