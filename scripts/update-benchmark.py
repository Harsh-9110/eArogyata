#!/usr/bin/env python3
"""Publish only numbers read from an actual k6 summary, including failed thresholds."""
import json,pathlib,re
root=pathlib.Path(__file__).resolve().parents[1]
source=root/'results/k6-summary.json'
if not source.exists():raise SystemExit('No k6 result exists. Run k6 first; benchmark numbers are never synthesized.')
report=json.loads(source.read_text());metrics=report['metrics'];meta=report['run_metadata']
count=metrics['committed_transactions']['values']['count'];latency=metrics['business_latency']['values'];failed=metrics['http_req_failed']['values']['rate']
checks=metrics['checks']['values'];thresholds={f'{name}: {key}':value['ok'] for name,metric in metrics.items() for key,value in metric.get('thresholds',{}).items()}
text=f'''| Measured API result | Value |
|---|---|
| Recorded at | {meta['recorded_at']} |
| Environment | {meta['environment']} |
| Committed mutation requests | {int(count)} |
| Business request p95 | {latency['p(95)']:.2f} ms |
| Business request p99 | {latency['p(99)']:.2f} ms |
| HTTP failure rate | {failed*100:.2f}% |
| Passed checks | {checks['passes']} / {checks['passes']+checks['fails']} |
| Thresholds | {'PASS' if all(thresholds.values()) else 'FAIL — inspect raw summary'} |

Source: [raw k6 summary](results/k6-summary.json). These are synthetic workload results on the stated environment, not patient outcomes.'''
file=root/'README.md';readme=file.read_text();updated=re.sub(r'<!-- BENCHMARK:START -->.*?<!-- BENCHMARK:END -->','<!-- BENCHMARK:START -->\n'+text+'\n<!-- BENCHMARK:END -->',readme,flags=re.S)
if updated==readme and '<!-- BENCHMARK:START -->' not in readme:raise SystemExit('README benchmark markers missing')
file.write_text(updated);print('README updated from actual k6 output.')
