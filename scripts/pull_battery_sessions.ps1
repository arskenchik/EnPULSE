param([string]$Serial='', [string]$Destination='results/battery')
$argsPrefix = if ($Serial) { @('-s',$Serial) } else { @() }
New-Item -ItemType Directory -Force -Path $Destination | Out-Null
& adb @argsPrefix pull /sdcard/Android/data/kaist.iclab.benchmark.week3/files/Benchmarks $Destination
python scripts/summarize_battery_sessions.py $Destination --out "$Destination/battery_tables.md"
