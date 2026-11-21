# JavaVision 一键重启 / 构建运行
#
# 为什么需要它：
#   应用直接以 `java -jar target/...jar` 运行时，Windows 会锁住该文件；
#   此时再打包，spring-boot 的 repackage 无法把 jar 改名成 .original，
#   结果 target 里留下一个没有 BOOT-INF/lib 的普通 jar（java -jar 起不来）。
#   本脚本改为：打包产物复制到独立目录再运行，target 永远不被锁。
#
# 用法：
#   .\tools\run.ps1              用现有产物重启（产物必须已 repackage 完成）
#   .\tools\run.ps1 -Build       先 mvn clean package，再复制并重启
#
# 说明：
#   - 只会终止监听 6888 的 java 进程，遇到非 java 占用会直接报错退出，不会乱杀。
#   - 启动前会剔除 SERVER__PORT / SERVER__HOST 环境变量（沙箱会注入，会顶掉 6888）。
#   - vectorex 的 MapDB 是单实例文件锁，所以启动前必须先确保没有残留实例。

param(
    [switch]$Build,
    [int]$Port = 6888,
    [string]$RunDir = 'D:\run',
    [string]$Mvn = 'D:\Java\apache-maven-3.6.3\bin\mvn.cmd',
    [string]$JavaHome = 'D:\Program Files\Java\jdk-11'
)

$ErrorActionPreference = 'Stop'

$projectDir = Split-Path -Parent $PSScriptRoot
$targetJar = Join-Path $projectDir 'target\JavaVision-0.0.1-SNAPSHOT.jar'
$backupJar = "$targetJar.original"
$runJar = Join-Path $RunDir 'JavaVision.jar'
$logFile = Join-Path $RunDir 'app.log'
$errFile = Join-Path $RunDir 'app.err.log'

function Stop-PortOwner {
    $conn = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
    if (-not $conn) {
        Write-Host "[stop] 端口 $Port 空闲"
        return
    }
    foreach ($procId in ($conn.OwningProcess | Select-Object -Unique)) {
        $proc = Get-Process -Id $procId -ErrorAction SilentlyContinue
        if (-not $proc) { continue }
        if ($proc.ProcessName -notlike 'java*') {
            throw "端口 $Port 被非 java 进程占用（PID $procId / $($proc.ProcessName)），请手工确认后再运行"
        }
        Stop-Process -Id $procId -Force
        Write-Host "[stop] 已终止 PID $procId（$($proc.ProcessName)）"
    }
    for ($i = 0; $i -lt 20; $i++) {
        Start-Sleep -Milliseconds 300
        if (-not (Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue)) { return }
    }
    throw "端口 $Port 在 6 秒内仍未释放"
}

function Invoke-Package {
    if (-not (Test-Path $Mvn)) { throw "找不到 mvn：$Mvn" }
    $env:JAVA_HOME = $JavaHome
    Write-Host "[build] mvn clean package ..."
    & $Mvn -B -f (Join-Path $projectDir 'pom.xml') clean package
    if ($LASTEXITCODE -ne 0) { throw "打包失败（exit $LASTEXITCODE）" }
    if (-not (Test-Path $backupJar)) {
        throw "repackage 未完成（缺少 .jar.original），产物不可执行。请先确认没有实例在运行该 jar"
    }
}

function Wait-Ready {
    for ($i = 0; $i -lt 45; $i++) {
        Start-Sleep -Seconds 2
        $code = & curl.exe --noproxy '*' -s -m 3 -o NUL -w '%{http_code}' "http://127.0.0.1:$Port/system/status" 2>$null
        if ($code -eq '200') {
            Write-Host "[ok] 服务就绪（约 $($i * 2 + 2) 秒），http://127.0.0.1:$Port"
            return $true
        }
    }
    return $false
}

Stop-PortOwner

if ($Build) {
    Invoke-Package
} elseif (-not (Test-Path $backupJar)) {
    throw "当前产物不完整（缺少 .jar.original，可能上次 repackage 失败）。请用 -Build 重新打包"
}

if (-not (Test-Path $targetJar)) { throw "找不到产物：$targetJar" }

New-Item -ItemType Directory -Force -Path $RunDir | Out-Null
Copy-Item $targetJar $runJar -Force
Write-Host ("[copy] {0:N1} MB -> {1}" -f ((Get-Item $runJar).Length / 1MB), $runJar)

# 沙箱注入的变量会覆盖 server.port / server.address
Remove-Item Env:SERVER__PORT, Env:SERVER__HOST -ErrorAction SilentlyContinue

$javaExe = Join-Path $JavaHome 'bin\java.exe'
Write-Host "[start] $javaExe -Xms1g -Xmx2g -jar $runJar"
Start-Process -FilePath $javaExe `
    -ArgumentList '-Xms1g', '-Xmx2g', '-jar', "`"$runJar`"" `
    -WorkingDirectory $projectDir `
    -RedirectStandardOutput $logFile `
    -RedirectStandardError $errFile `
    -WindowStyle Hidden

if (-not (Wait-Ready)) {
    Write-Host "[fail] 90 秒内未就绪，日志尾部："
    if (Test-Path $logFile) { Get-Content $logFile -Tail 40 }
    if (Test-Path $errFile) { Get-Content $errFile -Tail 20 }
    exit 1
}
