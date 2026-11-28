#!/usr/bin/env python3
"""Synthetic arrival simulation; this does not benchmark the API or a real hospital."""
import argparse,json,math,pathlib,random,statistics,re
p=argparse.ArgumentParser();p.add_argument('--seed',type=int,default=42);p.add_argument('--patients',type=int,default=500);args=p.parse_args()
if args.patients<10:raise SystemExit('Use at least 10 simulated patients')
rng=random.Random(args.seed);baseline=[];scheduled=[]
def waits(arrivals,durations):
 finish=0;out=[]
 for arrival,index in sorted((value,i) for i,value in enumerate(arrivals)):
  start=max(finish,arrival);out.append(start-arrival);finish=start+durations[index]
 return out
# Ten clinic days, three independent doctors per day; patients spread across the 30 queues.
for batch in range(30):
 n=args.patients//30+(1 if batch<args.patients%30 else 0)
 durations=[rng.lognormvariate(math.log(12)-0.35**2/2,0.35) for _ in range(n)]
 walk_in=[rng.uniform(0,90) for _ in range(n)]
 appointments=[max(0,i*12+rng.gauss(0,8)) for i in range(n)]
 baseline+=waits(walk_in,durations);scheduled+=waits(appointments,durations)
def stats(values):return {'mean_wait_minutes':round(statistics.mean(values),3),'p95_wait_minutes':round(sorted(values)[math.ceil(.95*len(values))-1],3)}
reduction=(statistics.mean(baseline)-statistics.mean(scheduled))/statistics.mean(baseline)*100
result={'label':'SIMULATED — not a measured clinical or API result','seed':args.seed,'patients':args.patients,'doctor_days':30,'assumptions':{'baseline':'Uniform arrivals during a 90-minute morning cluster','scheduled':'12-minute scheduled spacing with normal arrival jitter, SD 8 minutes, clamped to 0','service':'Identical paired lognormal consultation durations, mean 12 minutes, log-space SD 0.35','policy':'Non-preemptive FIFO by arrival in both scenarios; one clinician per queue','exclusions':'No urgent cases, breaks, no-shows, bed effects or patient outcomes; no calibration to real hospitals'},'clustered_arrivals':stats(baseline),'scheduled_arrivals':stats(scheduled),'mean_wait_reduction_percent_simulated':round(reduction,3)}
root=pathlib.Path(__file__).resolve().parents[1];target=root/'results/queue-simulation.json';target.parent.mkdir(exist_ok=True);target.write_text(json.dumps(result,indent=2)+'\n')
readme=root/'README.md'
if readme.exists():
 content=f"SIMULATED: {args.patients} synthetic patients across 30 doctor-days, seed {args.seed}. Mean wait: {result['clustered_arrivals']['mean_wait_minutes']:.2f} minutes under clustered arrivals versus {result['scheduled_arrivals']['mean_wait_minutes']:.2f} minutes under scheduled arrivals ({reduction:.2f}% simulated reduction). Both use the same FIFO policy and paired service durations. This tests arrival assumptions, not a new queue algorithm or a real clinical outcome. See [assumptions and raw simulation output](results/queue-simulation.json)."
 readme.write_text(re.sub(r'<!-- SIMULATION:START -->.*?<!-- SIMULATION:END -->','<!-- SIMULATION:START -->\n'+content+'\n<!-- SIMULATION:END -->',readme.read_text(),flags=re.S))
print(json.dumps(result,indent=2))
