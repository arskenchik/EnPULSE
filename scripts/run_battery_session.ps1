param(
    [Parameter(Mandatory=$true)][ValidateSet('B0','B1','B2','B2P','B3','B4','B5','B6')][string]$Condition,
  [int]$DurationMinutes=255, [int]$IntervalSeconds=30, [int]$MinimumSoc=20,
  [string]$Serial=''
)
$argsPrefix = if ($Serial) { @('-s',$Serial) } else { @() }
if ($Condition -in @('B5','B6')) { & adb @argsPrefix shell pm grant kaist.iclab.benchmark.week3 android.permission.RECORD_AUDIO }
& adb @argsPrefix shell am start-foreground-service -a kaist.iclab.benchmark.wearable.BATTERY -n kaist.iclab.benchmark.week3/kaist.iclab.benchmark.wearable.BenchmarkService --es extra_condition $Condition --es extra_scenario_name protocol --el extra_interval_ms ($IntervalSeconds*1000) --el extra_duration_minutes $DurationMinutes --ei extra_min_soc $MinimumSoc
