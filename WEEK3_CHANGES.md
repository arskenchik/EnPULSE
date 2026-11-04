# Week 3 changes

Base branch: `pradipta/demo-update`  
Base commit: `e780d412e6ad7590aefa401608f1b20712a3a642`

## Replay path

`app-wearable-benchmark` now contains an ADB-triggerable foreground replay
service. It reads the version-1 `ICLRPLY1` little-endian binary format, validates
the M1 `(50, 6)` input shape, normalizes each channel, and runs TensorFlow Lite
with XNNPACK and two threads.

- Accuracy mode evaluates every window once.
- Latency mode performs 20 warm-up windows followed by 100 measured windows,
  repeated for three runs.
- `predictions.csv` contains window ID, true label, predicted class, maximum
  logit, maximum probability, latency, run, and phase.
- `summary.txt` records mode, model, threading, window count, accuracy, and mean
  latency.

The included `m1_samosa_float32.tflite` is the Week 2 64-channel replacement
baseline (`SHA-256 06d87739076c1a86929fd27197d2892ce8fa7ffcaf58b2e6801c7870a6dea6db`).
It is a complete raw-IMU model and is not the missing original IMU encoder plus
lab-selected head.

## Battery session feature

- B0–B6 condition selector.
- Independent M1 and M2 gating-off flags recorded in folder and summary names.
- Default 30-second fuel-gauge, PSS, CPU, and thermal logging.
- Default 255-minute session duration.
- Automatic stop at 20% battery SoC.
- The metrics logger no longer requests or holds a permanent partial wakelock.

The gating flags are session metadata/control inputs. Connecting them to the
tracker model gates still requires the revised condition-to-runtime mapping.

## Commands

```powershell
.\gradlew.bat :app-wearable-benchmark:assembleDebug

.\scripts\run_wear_replay.ps1 `
  -Device <adb-serial> `
  -ReplayFile <path-to-samosa_m1_test.bin> `
  -Mode accuracy
```

The app writes results under
`/sdcard/Android/data/kaist.iclab.benchmark.week3/files/ReplayResults/`.

The debug build uses application ID `kaist.iclab.benchmark.week3`, allowing it
to be installed beside the original `kaist.iclab.benchmark` app without
replacing that app or modifying its data.
