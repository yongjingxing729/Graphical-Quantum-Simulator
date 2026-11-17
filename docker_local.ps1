# ============================================================
# 本地 Docker 一键运行（编译 -> 构建镜像 -> 启动容器）
# 用法（项目根目录）：
#   powershell -ExecutionPolicy Bypass -File .\docker_local.ps1
# ============================================================
$ErrorActionPreference = 'Continue'
$projectRoot = $PSScriptRoot
Set-Location $projectRoot

# 1) 编译 Java 源码到 prebuilt/classes21（--release 21，适配 JRE 21）
Write-Host "编译 Java 源码到 prebuilt/classes21 ..."
$out = Join-Path $projectRoot "prebuilt\classes21"
New-Item -ItemType Directory -Force -Path $out | Out-Null
$files = @()
$files += Get-ChildItem -Path (Join-Path $projectRoot "vendor\strange\src\main\java\org\redfx\strange") -Recurse -Filter *.java | ForEach-Object { $_.FullName }
$files += Get-ChildItem -Path (Join-Path $projectRoot "backend\src") -Recurse -Filter *.java | ForEach-Object { $_.FullName }
& javac --release 21 -encoding UTF-8 -d $out $files
if ($LASTEXITCODE -ne 0) {
    Write-Host "编译失败"
    exit $LASTEXITCODE
}

# 2) 构建并启动本地容器（仅映射到 127.0.0.1）
Write-Host "构建并启动本地容器 ..."
docker compose -f docker-compose.local.yml up -d --build
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

Write-Host ""
Write-Host "已启动。访问 http://127.0.0.1:8080"
Write-Host "查看日志: docker logs -f gqs-local"
Write-Host "停止:     docker compose -f docker-compose.local.yml down"
