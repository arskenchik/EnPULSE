param(
    [Parameter(Mandatory = $true)][string]$Device,
    [Parameter(Mandatory = $true)][string]$ReplayFile,
    [ValidateSet("M1", "M2", "M3")][string]$Model = "M1",
    [ValidateSet("accuracy", "latency")][string]$Mode = "accuracy",
    [string]$Apk = "app-wearable-benchmark/build/outputs/apk/debug/EnPULSE-Watch-Benchmark.apk",
    [string]$Output = "replay-results"
)

$ErrorActionPreference = "Stop"
$package = "kaist.iclab.benchmark.week3"
$remoteDir = "/sdcard/Android/data/$package/files/Replay"
$modelLower = $Model.ToLowerInvariant()
$remoteFile = "$remoteDir/samosa_${modelLower}_test.bin"
$remoteResults = "/sdcard/Android/data/$package/files/ReplayResults"

if (-not (Test-Path -LiteralPath $Apk)) { throw "APK not found: $Apk" }
if (-not (Test-Path -LiteralPath $ReplayFile)) { throw "Replay file not found: $ReplayFile" }

adb -s $Device install -r $Apk
if ($LASTEXITCODE -ne 0) { throw "APK installation failed" }
adb -s $Device shell mkdir -p $remoteDir
adb -s $Device push $ReplayFile $remoteFile
if ($LASTEXITCODE -ne 0) { throw "Replay transfer failed" }

adb -s $Device shell am start-foreground-service `
    -n "$package/kaist.iclab.benchmark.wearable.ReplayService" `
    -a "kaist.iclab.benchmark.wearable.REPLAY" `
    --es model $modelLower `
    --es mode $Mode
if ($LASTEXITCODE -ne 0) { throw "Replay service failed to start" }

Write-Host "Replay started in $Mode mode. Monitor with:"
Write-Host "adb -s $Device logcat -s M1ReplayService"
Write-Host "After completion, pull results with:"
Write-Host "adb -s $Device pull $remoteResults $Output"
