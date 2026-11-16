# Week 5 model battery sessions

The battery service now executes real model inference rather than recording only
a condition label.

| Condition | Sensor | Model | Window | Hop |
|---|---|---|---:|---:|
| B2 | IMU 50 Hz | M1 SaMOSA | 50 samples | 1 sample (20 ms) |
| B2P (B2') | IMU 50 Hz | M1 SaMOSA | 50 samples | 50 samples (1 s) |
| B3 | IMU 50 Hz | M2 SaMOSA | 100 samples | 50 samples (1 s) |
| B4 | IMU 10 Hz | M3 SaMOSA | 20 samples | 10 samples (1 s, provisional training hop) |
| B6 | Mic 16 kHz | YAMNet | 15,600 samples | 7,800 samples (0.4875 s) |

All TFLite runners use XNNPACK with two threads. Session summaries record model
load time, inference count, dropped windows, mean/p95/max latency, and first-vs-
last-five-minute p95 drift. B6 uses the same YAMNet model and tensor contract as
the existing tracker-library VAD implementation.

Because the SM-L325N delivered raw IMU callbacks below the requested 50 Hz, the
runtime now requests sensors at twice the target rate and samples the latest
real accelerometer/gyroscope state on a dedicated fixed-rate clock (50 Hz for
B2/B2P/B3 and 10 Hz for B4). The summary records both raw events and protocol
samples so the effective cadence is auditable.

The research APK is resource-shrunk and built for the watch image's reported
`armeabi-v7a` ABI. This reduces deployment size from 86.8 MB to 13.7 MB without
changing the side-by-side application ID.

`scripts/generate_yamnet_table_d.py` creates Table D directly from the raw B6
session and an optional full B5 reference session.

The APK must pass short B2/B2P/B3/B4/B6 on-watch activation checks before any
255-minute measurement is accepted.
