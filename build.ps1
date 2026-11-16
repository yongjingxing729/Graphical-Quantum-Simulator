# ============================================================
# 编译后端 + 上游 Strange 源码
# ------------------------------------------------------------
# 说明：本脚本只「编译」上游 vendor/strange 的源码副本，
#       绝不修改上游源码本身。后端仅调用上游公开 API。
# 用法：在项目根目录执行  .\build.ps1
# ============================================================
$ErrorActionPreference = 'Stop'
$projectRoot = $PSScriptRoot
$outDir = Join-Path $projectRoot "backend\build\classes"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

$strangeDir = Join-Path $projectRoot "vendor\strange\src\main\java\org\redfx\strange"
$backendDir = Join-Path $projectRoot "backend\src"

$files = @()
$files += Get-ChildItem -Path $strangeDir -Recurse -Filter *.java | ForEach-Object { $_.FullName }
$files += Get-ChildItem -Path $backendDir -Recurse -Filter *.java | ForEach-Object { $_.FullName }

Write-Host "编译 $($files.Count) 个源文件 -> $outDir"
& javac -encoding UTF-8 -d $outDir $files
if ($LASTEXITCODE -ne 0) {
    Write-Error "javac 编译失败，退出码 $LASTEXITCODE"
    exit $LASTEXITCODE
}
Write-Host "✅ 构建完成"
