#!/usr/bin/env python3
"""Generate protocol Table D from a B6 session and optional B5 reference."""
from __future__ import annotations
import argparse, csv, re
from pathlib import Path
from statistics import mean

def summary(path: Path) -> dict[str, str]:
    values = {}
    for line in path.read_text(encoding='utf-8').splitlines():
        if ':' in line:
            key, value = line.split(':', 1)
            values[key.strip()] = value.strip()
    return values

def number(text: str) -> float:
    match = re.search(r'-?[0-9]+(?:\.[0-9]+)?', text)
    if not match: raise ValueError(f'No number in {text!r}')
    return float(match.group())

def drain_rate(s: dict[str, str]) -> float:
    return number(s['Charge Dropped']) / number(s['Active Dur'])

def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument('b6', type=Path, help='B6 session folder')
    parser.add_argument('--b5', type=Path, help='Full B5 reference session folder')
    parser.add_argument('--capacity-uah', type=float, default=284_000)
    parser.add_argument('--out', type=Path, default=Path('table_d_yamnet.md'))
    args = parser.parse_args()
    b6 = summary(args.b6 / 'summary.txt')
    with (args.b6 / 'metrics.csv').open(newline='', encoding='utf-8') as stream:
        metrics = list(csv.DictReader(stream))
    b6_rate = drain_rate(b6)
    b5_rate = drain_rate(summary(args.b5 / 'summary.txt')) if args.b5 else None
    yamnet_only = b6_rate - b5_rate if b5_rate is not None else None
    p95 = number(b6['P95 Latency Ms'])
    drift = number(b6['P95 Drift Ms'])
    dropped = int(number(b6['Dropped Windows']))
    total = int(number(b6['Model Inferences'])) + dropped
    values = [
        ('Model load time (ms)', f"{number(b6['Model Load Ms']):.3f}", 'On-watch TFLite load'),
        ('p95 latency / 0.4875 s hop (%)', f'{p95 / 487.5 * 100:.3f}', 'Must remain below 100%'),
        ('p95 drift over run (ms)', f'{drift:.3f}', 'Last 5 min minus first 5 min'),
        ('Mic + YAMNet drain (uAh/h)', f'{b6_rate:.2f}', 'B6'),
        ('YAMNet-only cost (uAh/h)', f'{yamnet_only:.2f}' if yamnet_only is not None else 'pending B5', 'B6 minus B5'),
        ('Estimated runtime at B6 (h)', f'{args.capacity_uah / b6_rate:.2f}' if b6_rate > 0 else 'n/a', f'{args.capacity_uah:.0f} uAh capacity'),
        ('Mean PSS (MB)', f"{mean(float(row['app_memory_mb']) for row in metrics):.2f}", '30 s samples'),
        ('Thermal status (max)', str(max(int(row['thermal_status']) for row in metrics)), 'Android thermal code'),
        ('Dropped windows / total windows', f'{dropped} / {total}', 'B6 session'),
    ]
    lines = ['# Table D. YAMNet feasibility', '', '| Item | Value | Notes |', '|---|---:|---|']
    lines.extend(f'| {item} | {value} | {notes} |' for item, value, notes in values)
    args.out.write_text('\n'.join(lines) + '\n', encoding='utf-8')
    print(f'Wrote {args.out}')

if __name__ == '__main__': main()
