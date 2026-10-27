"""Actual Android evidence, using only RhinePreviewActivity's isolated in-memory Room database."""
from pathlib import Path
import subprocess
import time
import re
import json
import xml.etree.ElementTree as ET

ADB='/mnt/d/professionalTools/AndroidStorage/sdk/platform-tools/adb.exe'
OUT=Path(__file__).resolve().parent
COMPONENT='com.shub39.grit.debug/com.shub39.grit.rhine.RhinePreviewActivity'
_hud_bounds={}

def adb(*args,binary=False):
    if 'wm' in args:_hud_bounds.clear()
    return subprocess.check_output([ADB,*map(str,args)],text=not binary,timeout=25)

_tree_cache=None
_tree_time=0.0
def tree():
    global _tree_cache,_tree_time
    if _tree_cache is not None and time.monotonic()-_tree_time < .3:return _tree_cache
    for _ in range(4):
        dumped=adb('shell','uiautomator','dump','/sdcard/rhine03.xml')
        if 'dumped to' in dumped:
            _tree_cache=ET.fromstring(adb('exec-out','cat','/sdcard/rhine03.xml'));_tree_time=time.monotonic();return _tree_cache
        time.sleep(.5)
    raise AssertionError('Accessibility tree unavailable')

def nodes(label=None,desc=None,klass=None):
    return [n for n in tree().iter('node') if (label is None or n.get('text')==label) and (desc is None or n.get('content-desc')==desc) and (klass is None or n.get('class')==klass)]

def tap_node(n):
    x1,y1,x2,y2=map(int,re.findall(r'\d+',n.get('bounds')))
    adb('shell','input','tap',(x1+x2)//2,(y1+y2)//2)

def click(label=None,desc=None):
    if desc in _hud_bounds:
        tap_node(_hud_bounds[desc]);time.sleep(.65);return
    ns=nodes(label,desc)
    for _ in range(3):
        if ns:break
        time.sleep(.6);ns=nodes(label,desc)
    if not ns: raise AssertionError(f'Control missing: {label or desc}')
    if len(ns)>1: ns=[n for n in ns if n.get('class')!='android.widget.EditText'] or ns
    if desc in ['沿列上一格','沿列下一格','上一列','下一列','回到开头']:_hud_bounds[desc]=ns[0]
    tap_node(ns[-1] if label == "确认删除" else ns[0]);time.sleep(.65)

def expect(text):
    t=tree()
    assert any(text in (n.get('text','')+' '+n.get('content-desc','')) for n in t.iter('node')),text

def start(u=2,v=1,fail=False,trace=False,detail=True,dataset="detail",fail_delete=False,delay=500,adjust=False,fail_move=False,fail_search=False,search_delay=0,config=None,settings=False,reset=False):
    _hud_bounds.clear()
    adb('shell','am','start','-S','-n',COMPONENT,'--es','dataset',dataset,'--ef','u',u,'--ef','v',v,'--ez','detail',str(detail).lower(),'--ez','failSaveOnce',str(fail).lower(),'--ez','traceTouch',str(trace).lower(),'--ez','failDeleteOnce',str(fail_delete).lower(),'--el','submitDelayMs',str(delay),'--ez','adjust',str(adjust).lower(),'--ez','failMoveOnce',str(fail_move).lower(),'--ez','failSearchOnce',str(fail_search).lower(),'--el','searchDelayMs',search_delay)
    time.sleep(3)
    for attempt in range(30):
        try:
            expect('GRIT RHINE')
            break
        except AssertionError:
            if attempt == 29: raise
            print('Retry startup accessibility root',flush=True)
            time.sleep(1)
    if config is not None:
        mutate_display(config)
        actual=inspect_config()
        for key,value in config.items():assert actual[key]==value,(key,value,actual[key])
        if not config:assert actual['density']=='Standard' and actual['topSp']==8 and not actual['reduceMotion']

def shot(name):
    time.sleep(1)
    (OUT/name).write_bytes(adb('exec-out','screencap','-p',binary=True))
    import struct
    width,height=struct.unpack('>II',(OUT/name).read_bytes()[16:24])
    meta_path=OUT/'截图环境.json'
    metadata=json.loads(meta_path.read_text()) if meta_path.exists() else {}
    metadata[name]={'pixels':[width,height],'density':adb('shell','wm','density').strip(),'fontScale':adb('shell','settings','get','system','font_scale').strip()}
    meta_path.write_text(json.dumps(metadata,ensure_ascii=False,indent=2))
    print(name,flush=True)

def replace_field(index,text):
    ns=nodes(klass='android.widget.EditText');tap_node(ns[index]);time.sleep(.4)
    adb('shell','input','keycombination','113','29')
    adb('shell','input','text',text)
    time.sleep(.4)

def scroll(x=1800,y1=830,y2=340):
    adb('shell','input','swipe',x,y1,x,y2,600);time.sleep(.6)

def back():
    adb('shell','input','keyevent','4');time.sleep(.7)

def enter():
    click(desc='进入 Section 层');expect('SECTION LAYER');expect('TASK 02')

def record(name,actions,seconds=30):
    adb('shell','dumpsys','gfxinfo','com.shub39.grit.debug','reset')
    remote='/sdcard/'+name
    record_started=time.monotonic()
    events=[]
    proc=subprocess.Popen([ADB,'shell','screenrecord','--time-limit','180','--bit-rate','10000000',remote],stdout=subprocess.PIPE,stderr=subprocess.PIPE)
    time.sleep(1)
    recorder_pid=adb("shell","pidof","screenrecord").strip()
    assert recorder_pid.isdigit(), recorder_pid
    time.sleep(2)
    for index,action in enumerate(actions):
        events.append({"action":index,"start":time.monotonic()-record_started})
        action()
        events[-1]["end"]=time.monotonic()-record_started
        time.sleep(1)
    time.sleep(3)
    adb("shell","kill","-2",recorder_pid)
    stdout,stderr=proc.communicate(timeout=20)
    assert proc.returncode==0,(stdout,stderr)
    (OUT/name).write_bytes(adb('exec-out','cat',remote,binary=True))
    (OUT/(name.removesuffix('.mp4')+'-gfx.txt')).write_text(adb('shell','dumpsys','gfxinfo','com.shub39.grit.debug'))
    (OUT/(name.removesuffix('.mp4')+'-actions.json')).write_text(json.dumps(events,indent=2))
    print(name,flush=True)


def motion(kind,x,y):
    adb('shell','input','motionevent',kind,int(x),int(y))

def pickup(x=816,y=360):
    motion('DOWN',x,y);time.sleep(1)

def drag_to(x,y,duration=1.2,start=(816,360)):
    for i in range(1,13):
        motion('MOVE',start[0]+(x-start[0])*i/12,start[1]+(y-start[1])*i/12)
        time.sleep(duration/12)
    time.sleep(.5)

def release(x,y):
    motion('UP',x,y);time.sleep(2)

def texts():
    return [n.get('text') for n in tree().iter('node') if n.get('text')]

def mutate(name,**extras):
    args=['shell','am','start','-f','0x20000000','-n',COMPONENT,'--es','mutation',name]
    for k,v in extras.items():args+=['--el',k,str(v)]
    adb(*args);time.sleep(.8)

def inspect():
    mutate('inspect')
    return json.loads(adb('exec-out','run-as','com.shub39.grit.debug','cat','cache/rhine05-state.json'))


def mutate_display(config,save=False):
    # Shell quotes are required: am start's JSON payload passes through the device shell.
    import shlex
    payload=shlex.quote(json.dumps(dict(version=1,**config),separators=(',',':')))
    adb('shell','am','start','-f','0x20000000','-n',COMPONENT,'--es','mutation','save-display' if save else 'display','--es','displayConfig',payload)
    time.sleep(1)

def inspect_display():
    adb('shell','am','start','-f','0x20000000','-n',COMPONENT,'--es','mutation','inspect-display')
    time.sleep(.5)
    return json.loads(adb('exec-out','run-as','com.shub39.grit.debug','cat','cache/rhine06-state.json'))


def inspect_config():
    mutate("inspect-config")
    return json.loads(adb("exec-out","run-as","com.shub39.grit.debug","cat","cache/rhine07-config.json"))
