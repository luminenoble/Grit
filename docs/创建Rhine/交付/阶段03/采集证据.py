"""Actual Android evidence, using only RhinePreviewActivity's isolated in-memory Room database."""
from pathlib import Path
import subprocess
import time
import re
import xml.etree.ElementTree as ET

ADB='/mnt/d/professionalTools/AndroidStorage/sdk/platform-tools/adb.exe'
OUT=Path(__file__).resolve().parent
COMPONENT='com.shub39.grit.debug/com.shub39.grit.rhine.RhinePreviewActivity'

def adb(*args,binary=False):
    return subprocess.check_output([ADB,*map(str,args)],text=not binary,timeout=25)

def tree():
    for _ in range(4):
        dumped=adb('shell','uiautomator','dump','/sdcard/rhine03.xml')
        if 'dumped to' in dumped: return ET.fromstring(adb('exec-out','cat','/sdcard/rhine03.xml'))
        time.sleep(.5)
    raise AssertionError('Accessibility tree unavailable')

def nodes(label=None,desc=None,klass=None):
    return [n for n in tree().iter('node') if (label is None or n.get('text')==label) and (desc is None or n.get('content-desc')==desc) and (klass is None or n.get('class')==klass)]

def tap_node(n):
    x1,y1,x2,y2=map(int,re.findall(r'\d+',n.get('bounds')))
    adb('shell','input','tap',(x1+x2)//2,(y1+y2)//2)

def click(label=None,desc=None):
    ns=nodes(label,desc)
    if not ns: raise AssertionError(f'Control missing: {label or desc}')
    tap_node(ns[0]);time.sleep(.65)

def expect(text):
    t=tree()
    assert any(text in (n.get('text','')+' '+n.get('content-desc','')) for n in t.iter('node')),text

def start(u=2,v=1,fail=False,trace=False,detail=True):
    adb('shell','am','start','-S','-n',COMPONENT,'--es','dataset','detail','--ef','u',u,'--ef','v',v,'--ez','detail',str(detail).lower(),'--ez','failSaveOnce',str(fail).lower(),'--ez','traceTouch',str(trace).lower())
    time.sleep(3)
    expect('03 CATEGORIES');expect('GRIT RHINE')

def shot(name):
    time.sleep(1)
    (OUT/name).write_bytes(adb('exec-out','screencap','-p',binary=True))
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
    proc=subprocess.Popen([ADB,'shell','screenrecord','--time-limit',str(seconds),'--bit-rate','10000000',remote],stdout=subprocess.PIPE,stderr=subprocess.PIPE)
    time.sleep(1)
    for action in actions:
        action();time.sleep(1)
    stdout,stderr=proc.communicate(timeout=seconds+15)
    assert proc.returncode==0,(stdout,stderr)
    (OUT/name).write_bytes(adb('exec-out','cat',remote,binary=True))
    (OUT/(name.removesuffix('.mp4')+'-gfx.txt')).write_text(adb('shell','dumpsys','gfxinfo','com.shub39.grit.debug'))
    print(name,flush=True)

if __name__=='__main__':
    start();shot('01-category-selected-task-v1.png')
    enter();shot('03-section-zero-v1.png')
    click(desc='沿列下一格');shot('02-section-layer-v1.png')
    click(desc='编辑 Section');shot('04-section-editor-v1.png')
    scroll(1200,780,330);expect('完成日期');shot('05-section-date-v1.png')
    click(label='取消')
    click(desc='回到开头');click(desc='编辑 TASK');shot('07-task-description-color-v1.png')
    scroll();shot('08-task-options-v1.png')
    for _ in range(10):
        if any('2030' in n.get('text','') for n in tree().iter('node')): break
        scroll()
    expect('2030');shot('19-task-today-reminder-v1.png')
    click(label='取消');click(desc='返回类别层')
    expect('CATEGORY LAYER');expect('设计评审')
    click(desc='回到开头');click(desc='编辑 CATEGORY');shot('06-category-description-color-v1.png')
    replace_field(0,'KeyboardReview');shot('09-category-keyboard-v1.png')
    back();click(label='取消')
    start(fail=True);enter();click(desc='沿列下一格');click(desc='编辑 Section')
    replace_field(0,'Stage03Saved');back();click(label='保存')
    expect('模拟保存失败');expect('Stage03Saved');shot('10-failure-draft-v1.png')
    click(label='保存');expect('Stage03Saved');shot('11-section-saved-v1.png')
    click(desc='返回类别层');expect('设计评审')
    start(trace=True)
    record('12-enter-sibling-edit-return-v1.mp4',[
        enter,
        lambda:click(desc='下一列'),
        lambda:click(desc='上一列'),
        lambda:click(desc='沿列下一格'),
        lambda:click(desc='编辑 Section'),
        lambda:replace_field(0,'RecordedEdit'),
        back,
        lambda:click(label='保存'),
        lambda:click(desc='返回类别层'),
        lambda:adb('shell','input','tap',70,350)],seconds=70)
    expect('CATEGORY LAYER');expect('设计评审')
    shot('13-return-restored-v1.png')
    print('All capture assertions passed',flush=True)
