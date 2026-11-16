# ============================================================
# 编译并运行全部测试（Java 后端测试 + Python 单元测试）
# 用法：在项目根目录执行
#   powershell -ExecutionPolicy Bypass -File .\run_tests.ps1
# ============================================================
$ErrorActionPreference = 'Continue'
$projectRoot = $PSScriptRoot
$mainClasses = Join-Path $projectRoot "backend\build\classes"
$testClasses = Join-Path $projectRoot "backend\build\test-classes"

# 1) 确保主代码已编译
if (-not (Test-Path (Join-Path $mainClasses "com\gqs\Main.class"))) {
    Write-Host "未找到主代码编译产物，先构建..."
    & (Join-Path $projectRoot "build.ps1")
}

# 2) 编译测试代码
Write-Host "编译测试代码..."
New-Item -ItemType Directory -Force -Path $testClasses | Out-Null
$testFiles = Get-ChildItem -Path (Join-Path $projectRoot "backend\test") -Recurse -Filter *.java |
    ForEach-Object { $_.FullName }
& javac -encoding UTF-8 -cp $mainClasses -d $testClasses $testFiles
if ($LASTEXITCODE -ne 0) {
    Write-Host "测试编译失败"
    exit $LASTEXITCODE
}

# 3) 运行 Java 测试
Write-Host ""
Write-Host "运行 Java 测试..."
Push-Location $projectRoot
java -cp "$mainClasses;$testClasses" com.gqs.test.TestRunner
$javaCode = $LASTEXITCODE
Pop-Location

# 4) 运行 Python 单元测试
Write-Host ""
Write-Host "运行 Python 单元测试..."
Push-Location $projectRoot
python -m unittest discover -s (Join-Path $projectRoot "scripts\tests") -p "test_*.py" -v
$pyCode = $LASTEXITCODE
Pop-Location

if ($javaCode -ne 0 -or $pyCode -ne 0) {
    Write-Host ""
    Write-Host "[FAIL] 存在失败测试"
    exit 1
}
Write-Host ""
Write-Host "[OK] 所有测试通过"
