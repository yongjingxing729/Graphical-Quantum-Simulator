# ============================================================
# 运行后端服务器（本地模式默认绑定 127.0.0.1:8080）
# 用法：在项目根目录执行  .\run.ps1
# 切换本地/云端模式：修改 backend/src/com/gqs/AppConfig.java 中的
#   RUN_MODE 变量，或设置环境变量 $env:QSIM_MODE="cloud"
# ============================================================
$ErrorActionPreference = 'Stop'
$projectRoot = $PSScriptRoot
Set-Location $projectRoot

$classes = Join-Path $projectRoot "backend\build\classes"
if (-not (Test-Path (Join-Path $classes "com\gqs\Main.class"))) {
    Write-Host "未找到编译产物，先自动构建..."
    & (Join-Path $projectRoot "build.ps1")
}

java -cp $classes com.gqs.Main
