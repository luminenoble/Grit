"""Bounded actual Android workload; only debug in-memory fixtures. No file digests."""
from 采集工具 import *
import sys, statistics
TAG=sys.argv[1] if len(sys.argv)>1 else 'after'
LOG=OUT/'logs';LOG.mkdir(exist_ok=True)

def perf():
    mutate('inspect-perf')
    return json.loads(adb('exec-out','run-as','com.shub39.grit.debug','cat','cache/rhine07-perf.json'))

def summarize(data):
    frames=[x for x in data['frames'] if not x['first'] and x['totalNs']>0]
    result={k:v for k,v in data.items() if k not in ['frames','ranges']}
    for metric in ['totalNs','drawNs','layoutNs']:
        vals=sorted(x[metric]/1e6 for x in frames)
        if vals:result[metric]={'n':len(vals),'p50':statistics.median(vals),'p90':vals[min(len(vals)-1,int(len(vals)*.9))],'p95':vals[min(len(vals)-1,int(len(vals)*.95))],'max':max(vals),'over16_67':sum(v>16.6667 for v in vals)}
    return result

results={}
def sample(name,work):
    mutate('reset-perf');adb('shell','dumpsys','gfxinfo','com.shub39.grit.debug','reset')
    begin=time.monotonic();work();duration=time.monotonic()-begin
    data=perf();data['workSeconds']=duration
    (LOG/f'{TAG}-{name}-metrics.json').write_text(json.dumps(data,ensure_ascii=False,indent=2))
    (LOG/f'{TAG}-{name}-gfx.txt').write_text(adb('shell','dumpsys','gfxinfo','com.shub39.grit.debug','framestats'))
    memory=adb('shell','dumpsys','meminfo','com.shub39.grit.debug');(LOG/f'{TAG}-{name}-memory.txt').write_text(memory)
    results[name]=summarize(data);results[name]['workSeconds']=duration
    print(name,json.dumps(results[name],ensure_ascii=False),flush=True)

def browse(n):
    for i in range(n):
        args=(600,430,1800,783) if i%2==0 else (1800,783,600,430)
        adb('shell','input','swipe',*args,400);time.sleep(.35)

def enter_return():
    for _ in range(5):click(desc='进入 Section 层');click(desc='返回类别层')

def settings_save():
    for name in ['紧凑','标准','宽松','标准']:
        click(label='SETTING ↗');click(label=name);click(label='保存');click(label='取消')

def sorts():
    for pos in ['2','8','2','8']:
        click(desc='移动到第 N 项');replace_field(0,pos);back();click(label='确认移动')

def searches():
    for text in ['Task%s8','zzzz','Task']:
        click(desc='搜索 Task 标题');replace_field(0,text);back();click(label='关闭搜索')

start(u=50,v=49,dataset='large',detail=False)
mutate_display({},save=True)
raw=adb('exec-out','run-as','com.shub39.grit.debug','cat','files/datastore/rhine06-isolated.preferences_pb',binary=True)
actual=next(json.loads(x) for x in re.findall(rb'\{[^{}]+\}',raw) if json.loads(x).get('version')==1)
assert actual['density']=='Standard' and actual['topSp']==8 and actual['sideSp']==7
(LOG/f'{TAG}-actual-config.json').write_text(json.dumps(actual,indent=2))
time.sleep(5);(LOG/f'{TAG}-large-start-metrics.json').write_text(json.dumps(perf(),ensure_ascii=False,indent=2))
shot(f'{TAG}-large-before.png')
sample('large-idle',lambda:time.sleep(10))
sample('large-browse20',lambda:browse(20))
if TAG in ['before','final']:
    (LOG/f'{TAG}-summary.json').write_text(json.dumps(results,ensure_ascii=False,indent=2))
    print('DONE',TAG,flush=True)
    sys.exit(0)
for cycle in range(3):sample(f'large-loop{cycle+1}',lambda:browse(20))
shot(f'{TAG}-large-after.png')
start(u=101,v=49,dataset='large',detail=False,config={})
def tail():
    for _ in range(10):adb('shell','input','swipe',600,430,1800,783,300);time.sleep(.3)
sample('large-tail10',tail)
start(u=1,v=0,dataset='sections1000',detail=True,config={});click(desc='进入 Section 层');time.sleep(3)
(LOG/f'{TAG}-sections-start-metrics.json').write_text(json.dumps(perf(),ensure_ascii=False,indent=2))
sample('sections-browse20',lambda:browse(20))
start(u=8,v=0,dataset='adjust',detail=True,config={});time.sleep(3)
sample('enter-return5',enter_return)
sample('search3',searches)
sample('settings4',settings_save)
click(desc='切换预览/详情模式');sample('sort4',sorts)
(LOG/f'{TAG}-summary.json').write_text(json.dumps(results,ensure_ascii=False,indent=2))
print('DONE',TAG,flush=True)
