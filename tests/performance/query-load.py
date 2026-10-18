#!/usr/bin/env python3
"""Twenty concurrent users for >=300s, after warmup. Run against isolated seeded data."""
import argparse, concurrent.futures, json, os, platform, statistics, subprocess, threading, time, urllib.request
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--seconds',type=int,default=300);p.add_argument('--warmup',type=int,default=20);args=p.parse_args()
if args.seconds<300: raise SystemExit('Acceptance load must last at least 300 seconds')
base=os.getenv('REDNOTE_URL','http://localhost:8085')
fixture=json.loads(Path('target/browser-fixture.json').read_text())
paths=['/','/search?q=benchmark',fixture['userPath'],fixture['notePath']]
records={path:[] for path in paths}; failures=[];lock=threading.Lock()
def load(index,until,measure):
 i=index
 while time.monotonic()<until:
  path=paths[i%len(paths)];started=time.perf_counter()
  try:
   with urllib.request.urlopen(base+path,timeout=30) as r:
    r.read()
    if r.status!=200:raise RuntimeError(str(r.status))
   elapsed=(time.perf_counter()-started)*1000
   if measure:
    with lock:records[path].append(elapsed)
  except Exception as e:
   if measure:
    with lock:failures.append({'path':path,'error':str(e)})
  i+=1;time.sleep(.05)
def phase(seconds,measure):
 until=time.monotonic()+seconds
 with concurrent.futures.ThreadPoolExecutor(max_workers=20) as pool:
  list(pool.map(lambda i:load(i,until,measure),range(20)))
phase(args.warmup,False);started=time.monotonic();phase(args.seconds,True)
result={'utcTime':time.strftime('%Y-%m-%dT%H:%M:%SZ',time.gmtime()),'platform':platform.platform(),'machine':platform.machine(),'cpuCount':os.cpu_count(),'concurrency':20,'warmupSeconds':args.warmup,'durationSeconds':time.monotonic()-started,'thinkSeconds':.05,'includesLocalTransfer':True,'failures':failures,'queries':{}}
for path,values in records.items():
 values.sort();result['queries'][path]={'count':len(values),'p50Ms':round(statistics.median(values),2),'p95Ms':round(values[min(len(values)-1,int(len(values)*.95))],2),'maxMs':round(max(values),2)} if values else {'count':0}
result['passed']=not failures and all(v.get('p95Ms',float('inf'))<=1000 for v in result['queries'].values())
Path('target/performance-result.json').write_text(json.dumps(result,ensure_ascii=False,indent=2));print(json.dumps(result,ensure_ascii=False,indent=2))
raise SystemExit(0 if result['passed'] else 1)
