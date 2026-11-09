#!/usr/bin/env python3
"""Convert pulled EnPULSE session folders into protocol Tables C and E."""
import argparse, csv, re
from pathlib import Path
from statistics import mean

def load(folder):
    with (folder/'metrics.csv').open(newline='', encoding='utf-8') as f: rows=list(csv.DictReader(f))
    if len(rows)<2: raise ValueError(f'{folder}: needs at least two samples')
    a,b=rows[0],rows[-1]; hours=(int(b['timestamp_ms'])-int(a['timestamp_ms']))/3_600_000
    match=re.search(r'(B[0-6])', folder.name)
    return {'folder':str(folder),'date':a['timestamp_iso'][:10],'condition':match.group(1) if match else '?','rate':(int(a['battery_charge_uah'])-int(b['battery_charge_uah']))/hours,'soc':a['battery_level_pct'],'start':a['battery_charge_uah'],'end':b['battery_charge_uah'],'temp':a['battery_temp_c'],'pss':mean(float(x['app_memory_mb']) for x in rows),'max_temp':max(float(x['cpu_temperature_c']) for x in rows)}

def main():
    p=argparse.ArgumentParser(); p.add_argument('root',type=Path); p.add_argument('--out',type=Path,default=Path('battery_tables.md')); a=p.parse_args()
    runs=[load(x.parent) for x in a.root.rglob('metrics.csv')]; b0=[x['rate'] for x in runs if x['condition']=='B0']; baseline=mean(b0) if b0 else None
    lines=['# Battery results','','## Table C','','| Condition | Runs | Mean drain (uAh/h) | Net (uAh/h) | x baseline | Mean PSS (MB) | Max temp (C) |','|---|---:|---:|---:|---:|---:|---:|']
    for c in sorted(set(x['condition'] for x in runs)):
        rs=[x for x in runs if x['condition']==c]; rate=mean(x['rate'] for x in rs); net=f'{rate-baseline:.2f}' if baseline else '—'; ratio=f'{rate/baseline:.2f}' if baseline else '—'
        lines.append(f"| {c} | {len(rs)} | {rate:.2f} | {net} | {ratio} | {mean(x['pss'] for x in rs):.2f} | {max(x['max_temp'] for x in rs):.1f} |")
    lines += ['','## Table E','','| Date | Condition | Run | Start SoC | Start charge | End charge | Start temp | Log folder |','|---|---|---:|---:|---:|---:|---:|---|']; counts={}
    for x in runs:
        counts[x['condition']]=counts.get(x['condition'],0)+1; lines.append(f"| {x['date']} | {x['condition']} | {counts[x['condition']]} | {x['soc']}% | {x['start']} | {x['end']} | {x['temp']} | `{x['folder']}` |")
    a.out.write_text('\n'.join(lines)+'\n',encoding='utf-8'); print(f'Wrote {a.out} from {len(runs)} session(s)')
if __name__=='__main__': main()
