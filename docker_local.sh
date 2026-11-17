#!/usr/bin/env bash
# ============================================================
# 本地 Docker 一键运行（编译 -> 构建镜像 -> 启动容器）
# 用法：./docker_local.sh
# ============================================================
set -euo pipefail
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$PROJECT_ROOT"

echo "编译 Java 源码到 prebuilt/classes21 ..."
OUT="$PROJECT_ROOT/prebuilt/classes21"
mkdir -p "$OUT"
mapfile -t FILES < <(find "$PROJECT_ROOT/vendor/strange/src/main/java/org/redfx/strange" "$PROJECT_ROOT/backend/src" -name '*.java')
javac --release 21 -encoding UTF-8 -d "$OUT" "${FILES[@]}"

echo "构建并启动本地容器 ..."
docker compose -f docker-compose.local.yml up -d --build

echo ""
echo "已启动。访问 http://127.0.0.1:8080"
echo "查看日志: docker logs -f gqs-local"
echo "停止:     docker compose -f docker-compose.local.yml down"
