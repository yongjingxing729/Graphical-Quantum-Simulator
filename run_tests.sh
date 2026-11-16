#!/usr/bin/env bash
# ============================================================
# 编译并运行全部测试（Java 后端测试 + Python 单元测试）
# 用法：./run_tests.sh
# ============================================================
set -euo pipefail
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MAIN_CLASSES="$PROJECT_ROOT/backend/build/classes"
TEST_CLASSES="$PROJECT_ROOT/backend/build/test-classes"

if [ ! -f "$MAIN_CLASSES/com/gqs/Main.class" ]; then
  echo "未找到主代码编译产物，先构建..."
  "$PROJECT_ROOT/build.sh"
fi

mkdir -p "$TEST_CLASSES"
mapfile -t TEST_FILES < <(find "$PROJECT_ROOT/backend/test" -name '*.java' -print)
echo "编译测试代码 (${#TEST_FILES[@]} 个文件)..."
javac -encoding UTF-8 -cp "$MAIN_CLASSES" -d "$TEST_CLASSES" "${TEST_FILES[@]}"

cd "$PROJECT_ROOT"
echo ""
echo "运行 Java 测试..."
java -cp "$MAIN_CLASSES:$TEST_CLASSES" com.gqs.test.TestRunner

echo ""
echo "运行 Python 单元测试..."
PYTHON_BIN="$(command -v python3 || command -v python)"
"$PYTHON_BIN" -m unittest discover -s "$PROJECT_ROOT/scripts/tests" -p "test_*.py" -v
