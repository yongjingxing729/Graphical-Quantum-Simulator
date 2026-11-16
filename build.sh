#!/usr/bin/env bash
# ============================================================
# 编译后端 + 上游 Strange 源码（Linux / macOS / 云端容器）
# 用法：./build.sh
# ============================================================
set -euo pipefail
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUT_DIR="$PROJECT_ROOT/backend/build/classes"
mkdir -p "$OUT_DIR"

STRANGE_DIR="$PROJECT_ROOT/vendor/strange/src/main/java/org/redfx/strange"
BACKEND_DIR="$PROJECT_ROOT/backend/src"

mapfile -t FILES < <(find "$STRANGE_DIR" "$BACKEND_DIR" -name '*.java' -print)
echo "编译 ${#FILES[@]} 个源文件 -> $OUT_DIR"
javac -encoding UTF-8 -d "$OUT_DIR" "${FILES[@]}"
echo "✅ 构建完成"
