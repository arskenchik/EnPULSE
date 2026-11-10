# Week 4: revised battery protocol and M1 CMI audit

## Implemented

- Battery conditions B0-B6 have explicit sensor specifications.
- B0 runs the 30-second resource/fuel-gauge logger only.
- B1 registers accelerometer and gyroscope at 50 Hz.
- B5 continuously reads mono PCM audio at 16 kHz without storing raw audio.
- Sessions default to 255 minutes and stop at 20% SoC.
- Duration auto-stop uses an exact allow-while-idle system alarm, not a service
  Handler, so Wear OS idle scheduling cannot extend the session.
- Metrics include charge, current, voltage, PSS, thermal status and temperature.
- No permanent wakelock is requested or held.
- Sessions can be started through ADB with `scripts/run_battery_session.ps1`.
- Pulled sessions are converted to protocol Tables C and E by
  `scripts/summarize_battery_sessions.py`.

## Commands

Dry run (five minutes):

```powershell
.\scripts\run_battery_session.ps1 -Condition B0 -DurationMinutes 5 -Serial WATCH
```

Protocol run:

```powershell
.\scripts\run_battery_session.ps1 -Condition B1 -Serial WATCH
```

Extraction and tabulation:

```powershell
.\scripts\pull_battery_sessions.ps1 -Serial WATCH -Destination results/battery/watch2
```

## Scope boundary

B2-B4 and B6 currently activate their required sensor streams, but continuous
live model inference is not claimed by this change. Week 4 measurements use only
the model-independent B0, B1 and B5 conditions.

The available public CMI `test.csv` is an unlabeled competition inference set:
107 rows, two sequences, two subjects and no gesture target. It cannot be used
to train M1 or calculate Table A-2 accuracy. The fixed labeled CMI split and M1
training implementation/checkpoint are still required; no fabricated metric is
reported.
