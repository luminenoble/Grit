"""Mutations and UI assertions only touch the debug activity's memory database."""
import 采集工具 as c
import time
from pathlib import Path
checks=[]
def done(name):
    checks.append(name); print('PASS '+name,flush=True)
    Path(__file__).with_name('交互断言结果.txt').write_text('\n'.join('PASS '+n for n in checks)+'\n')
def absent(text): assert not any(text in n.get('text','') for n in c.tree().iter('node')), text

def mutate(name):
    c.adb('shell','am','start','-f','0x20000000','-n',c.COMPONENT,'--es','mutation',name);time.sleep(1.5)

def cancel_task():
    c.click(label='取消')
    if c.nodes(label='放弃'): c.click(label='放弃')

def burst(label):
    n=c.nodes(label=label)[-1] if label == '确认删除' else c.nodes(label=label)[0]
    import re
    x1,y1,x2,y2=map(int,re.findall(r'\d+',n.get('bounds')));x=(x1+x2)//2;y=(y1+y2)//2
    c.adb('shell',f'input tap {x} {y}; input tap {x} {y}; input tap {x} {y}')
    time.sleep(1.5)

c.start(99,1,dataset='uneven',detail=False);absent('尾部新增 TASK  ＋')
c.adb('shell','input','tap',816,359);time.sleep(1);absent('Task details');c.expect('102 TASKS');done('preview placeholder tap creates no objects')
c.start(99,1,dataset='uneven');c.click(desc='尾部新增 TASK');c.click(label='取消');c.expect('99');c.expect('/  01');c.expect('102 TASKS');done('cancel distant Task creation preserves counts and U99')
c.click(desc='尾部新增 TASK');c.replace_field(0,'OnlyOnce');c.back();burst('保存');c.expect('02   OnlyOnce');c.expect('/  02');c.expect('103 TASKS');done('rapid save submits one tail item and locates U2 by returned ID')
c.click(desc='删除焦点对象');burst('确认删除');c.expect('01   B');c.expect('102 TASKS');done('rapid deletion removes once and falls back to predecessor')

c.start(2,1);c.click(desc='删除焦点对象');c.click(label='取消删除');c.click(label='取消');c.expect('02   设计评审');c.expect('09 TASKS');done('cancel cascade confirmation leaves focused Task and camera unchanged')
c.click(desc='删除焦点对象');c.click(label='确认删除');time.sleep(1);c.expect('02   空任务');c.expect('08 TASKS');done('delete middle Task chooses same-position successor')

c.start();c.enter();c.click(desc='沿列下一格');mutate('import-reused');c.expect('CATEGORY LAYER');c.expect('00   学习');done('full import with reused IDs invalidates both cameras, parent snapshot and cache')
c.click(desc='下一列');c.click(desc='沿列下一格');c.click(desc='沿列下一格');c.enter();c.expect('00   设计评审');done('re-enter after import does not restore cached Section U1')

c.start();c.enter();c.click(desc='沿列下一格');c.click(desc='返回类别层');mutate('reorder-section');c.enter();c.expect('04   需求与范围');done('external reorder still restores cached Section by stable identity')
c.start();c.enter();mutate('move');c.expect('SECTION LAYER');c.click(desc='返回类别层');c.expect('CATEGORY 03');c.expect('03   设计评审');done('external move preserves parent snapshot and follows current Category on return')
c.start();c.enter();mutate('delete-category');c.expect('CATEGORY LAYER');c.expect('CATEGORY 02');done('external deletion of owning Category exits Section layer')

c.start(99,1,dataset='uneven');
try:
    c.adb('shell','wm','size','1080x2000');time.sleep(1);c.expect('99');c.expect('/  01')
finally: c.adb('shell','wm','size','reset')
time.sleep(1);c.expect('99');done('resize retains distant short-column position')

c.start(0,0,dataset='empty');c.click(desc='新建 CATEGORY');c.click(label='取消');c.expect('00 CATEGORIES');c.expect('00');done('cancel unique empty start leaves no Category or implicit Task')
c.start(0,0,dataset='one-empty',fail_delete=True);c.click(desc='删除焦点对象')
c.tap_node(c.nodes(label='确认删除')[-1]);c.adb('shell','input','keyevent','4');time.sleep(1)
c.expect('模拟删除失败');c.expect('确认删除');done('Android back during delete cannot dismiss pending operation; failure preserves confirmation')
c.click(label='确认删除');time.sleep(1);c.expect('00 CATEGORIES');done('retry after failed delete commits once and reaches empty start')

c.start(1,0,dataset='section-uneven');c.click(desc='进入 Section 层')
c.click(desc='删除焦点对象');c.expect('全部 Section');c.click(label='确认删除');time.sleep(1)
c.expect('00   设计评审');c.expect('01 TASKS');done('deleting longest Task column cascades Sections and selects sibling zero')
c.click(desc='删除焦点对象');c.click(label='确认删除');time.sleep(1);c.expect('00 TASKS');c.expect('新建 TASK')
c.click(desc='新建 TASK');c.click(label='取消');c.expect('00 TASKS');done('Section layer with no Tasks keeps unique new sibling start and cancel is inert')

c.start(2,1);c.enter();c.click(desc='下一列');c.click(desc='沿列下一格')
c.click(desc='尾部新增 SECTION');c.expect('unname');c.click(label='取消');c.expect('/  00');c.expect('22 SECTIONS')
done('cancel new Section in empty Task writes nothing and preserves shared U')

c.start(0,0,dataset='one-empty',fail_delete=True);c.click(desc='删除焦点对象')
try:
    c.tap_node(c.nodes(label='确认删除')[-1]);c.adb('shell','wm','size','1080x2000');time.sleep(1)
    c.expect('模拟删除失败');c.expect('确认删除')
finally: c.adb('shell','wm','size','reset')
time.sleep(1);c.click(label='取消删除');c.click(label='取消');c.expect('01 CATEGORIES')
done('resize during delete retains retry context; cancel preserves data')

c.start(100,0,dataset='uneven')
c.adb('shell','input','swipe',650,420,1500,670,90)
c.adb('shell','am','start','-f','0x20000000','-n',c.COMPONENT,'--es','mutation','delete-task','--el','taskId','1099')
time.sleep(2);assert not c.nodes(label='101');c.expect('101 TASKS')
done('external longest tail deletion during fling invalidates old U101 boundary')

print(f'{len(checks)} checks passed',flush=True)
