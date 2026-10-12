"""Assertion-based UI regression against debug fixtures; never opens or replaces user data."""
import importlib.util
from pathlib import Path
import time

spec=importlib.util.spec_from_file_location('capture',Path(__file__).with_name('采集证据.py'))
c=importlib.util.module_from_spec(spec);spec.loader.exec_module(c)
checks=[]
def done(name): checks.append(name);print('PASS '+name,flush=True)
def absent(text): assert not any(text in n.get('text','') for n in c.tree().iter('node')),text

def mutate(name):
    c.adb('shell','am','start','-f','0x20000000','-n',c.COMPONENT,'--es','mutation',name)
    time.sleep(1)

c.start(detail=False)
c.click(desc='进入 Section 层') if c.nodes(desc='进入 Section 层') else None
c.expect('CATEGORY LAYER')
c.adb('shell','input','tap',816,359);time.sleep(.8);c.expect('CATEGORY LAYER')
c.click(desc='切换预览/详情模式');c.expect('MODE  DETAIL');done('preview taps only position; mode switches to detail')

c.start()
c.adb('shell','input','tap',313,885);time.sleep(1)
c.expect('CATEGORY LAYER');c.expect('CATEGORY 01');absent('SECTION LAYER')
c.adb('shell','input','tap',816,359);time.sleep(1)
c.expect('SECTION LAYER');c.expect('02 TASKS');done('other column first tap only positions; stable second tap enters scoped siblings')

c.start();c.adb('shell','input','tap',901,391);time.sleep(1);c.expect('SECTION LAYER');c.expect('TASK 01');c.click(desc='返回类别层');c.expect('02   设计评审');done('entering another U slot preserves the original parent Task and offsets')

c.start()
c.adb('shell','input','tap',1474,760)
c.adb('shell','input','tap',1474,760)
c.adb('shell','input','tap',1474,760)
time.sleep(1);c.expect('SECTION LAYER');absent('任务详情');done('repeated entry clicks do not open nested forms')
c.click(desc='沿列下一格');c.click(desc='编辑 Section')
c.replace_field(0,'CancelledDraft');c.back();c.click(label='取消')
c.expect('需求与范围');absent('CancelledDraft');done('cancel leaves stored Section title unchanged')
c.click(desc='编辑 Section');c.scroll(1200,780,330)
fields=c.nodes(klass='android.widget.EditText');date_index=next(i for i,n in enumerate(fields) if n.get('text')=='2026-01-02')
c.replace_field(date_index,'2099-01-01');c.back();c.click(label='保存');c.expect('编辑 Section');c.expect('不能晚于今天')
fields=c.nodes(klass='android.widget.EditText');date_index=next(i for i,n in enumerate(fields) if n.get('text')=='2099-01-01')
c.replace_field(date_index,'2026-01-03');c.scroll(1200,240,290);c.expect('保存');c.expect('取消');c.shot('17-section-date-keyboard-v1.png');c.click(label='保存');c.expect('DATE 2026-01-03')
c.shot('18-section-date-saved-v1.png');done('future date cannot save; past date saves and refreshes HUD')
c.click(desc='返回类别层');c.expect('CATEGORY LAYER');c.expect('02   设计评审')
c.enter();c.expect('01   需求与范围');done('parent ID/U/V restore and per-Task Section position resume')
c.back();c.expect('CATEGORY LAYER');c.expect('02   设计评审');done('Android back matches HUD return')

c.start();c.adb('shell','input','tap',1474,760);c.adb('shell','input','keyevent','4');time.sleep(1)
c.expect('CATEGORY LAYER');c.expect('02   设计评审');done('back during entry cancels transition and restores parent')

c.start();c.enter();c.click(desc='下一列');c.expect('空任务');c.expect('/  00')
c.click(desc='沿列下一格');c.expect('空位置');c.shot('14-empty-task-shared-u-v1.png');done('empty Task uses shared U and finite placeholder')
c.click(desc='上一列');c.expect('设计评审')
try:
    c.adb('shell','wm','size','1080x2000');time.sleep(1);c.expect('设计评审');c.shot('15-resized-section-v1.png')
finally: c.adb('shell','wm','size','reset')
time.sleep(1);c.expect('设计评审');done('viewport resize preserves Task/Section focus')

c.start();c.enter();mutate('rename');c.expect('外部修改');c.shot('16-external-rename-color-v1.png')
c.click(desc='返回类别层');c.expect('外部修改');done('external title/color refresh; return follows stable Task ID')
c.start();c.enter();mutate('move');c.expect('SECTION LAYER');absent('载入中…')
c.click(desc='返回类别层');c.expect('CATEGORY 03');c.expect('03   设计评审');done('moved Task leaves sibling directory; parent return follows new Category')
c.start();c.enter();c.click(desc='沿列下一格');mutate('reorder-section');c.expect('04   需求与范围');done('live Section reorder keeps stable identity and follows its new index')
c.start();c.enter();c.click(desc='沿列下一格');c.click(desc='返回类别层');mutate('reorder-section');c.enter();c.expect('04   需求与范围');done('cached Section position restores by ID after external reorder')
c.start();c.enter();mutate('delete-task');c.expect('SECTION LAYER');c.expect('空任务')
c.click(desc='返回类别层');c.expect('CATEGORY LAYER');c.expect('空任务');done('deleted Task falls back to real neighbour in surviving Category')
c.start();c.enter();c.click(desc='沿列下一格');mutate('delete-section');c.expect('01   草图评审');done('deleted focused Section resolves to the surviving item without stale ID')
c.start();c.enter();mutate('delete-category');c.expect('CATEGORY LAYER');c.expect('CATEGORY 02');done('deleted parent Category exits second layer to surviving neighbour')

(Path(__file__).parent/'交互断言结果.txt').write_text('\n'.join('PASS '+name for name in checks)+'\n')
print(f'{len(checks)} UI checks passed',flush=True)
