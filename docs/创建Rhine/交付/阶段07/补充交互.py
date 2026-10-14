from 采集工具 import *
adb('shell','settings','put','secure','show_ime_with_hard_keyboard','1')
adb('shell','settings','put','secure','stylus_handwriting_enabled','0')

# All state belongs to the debug Activity. No user preferences or business DB are opened.
start(u=8,v=0,dataset='adjust',adjust=True,config={})
pickup();expect('取消移动');mutate_display(dict(topSp=12));release(816,360)
assert inspect()['commits']==inspect()['attempts']==0;expect('08   Task 8')
print('PASS live display change cancels picked source and auto-scroll without save',flush=True)
pickup();expect('取消移动');adb('shell','wm','size','1080x1920');time.sleep(1);release(816,360)
assert inspect()['commits']==inspect()['attempts']==0
adb('shell','wm','size','reset');time.sleep(2)
print('PASS viewport change cancels drag without save',flush=True)
for c in [dict(reduceMotion=True,shadow='Off'),dict(shadow='Low')]:
    start(u=8,v=0,dataset='adjust',adjust=True,config=c)
    click(desc='移动到第 N 项');replace_field(0,'2');back();click(label='确认移动');expect('02   Task 8')
    assert inspect()['commits']==1
print('PASS reduced animations and shadow modes preserve sorting',flush=True)
for key in ['animator_duration_scale','transition_animation_scale','window_animation_scale']:
    adb('shell','settings','put','global',key,0)
try:
    start(u=8,v=0,dataset='adjust',adjust=True,config={})
    click(desc='移动到第 N 项');replace_field(0,'2');back();click(label='确认移动');expect('02   Task 8')
    assert inspect()['commits']==1
    print('PASS system animation scales zero preserve sorting',flush=True)
finally:
    adb('shell','settings','delete','global','animator_duration_scale')
    for key in ['transition_animation_scale','window_animation_scale']:adb('shell','settings','put','global',key,'1.0')
start(u=1,v=0,dataset='adjust',detail=True,config={})
click(desc='进入 Section 层');expect('SECTION LAYER');click(desc='搜索 Task 标题');replace_field(0,'Task%s8');back();click(label='Task 8');expect('CATEGORY LAYER');expect('08   Task 8');expect('MODE  PREVIEW')
print('PASS lower search restores current upper ID in preview',flush=True)
start(u=8,v=0,dataset='adjust',adjust=True,config={})
click(desc='移动到第 N 项');mutate('import-reused');assert not nodes(label='确认移动');expect('00   学习')
assert inspect()['attempts']==0
print('PASS imported reused IDs invalidate position and focus context',flush=True)
start(u=99,v=1,dataset='uneven',detail=False,config=dict(density='Compact',targetRows=16,targetColumns=4))
expect('尾部追加');click(desc='回到开头');expect('00   B · 短列')
start(u=101,v=0,dataset='uneven',detail=False,config=dict(density='Custom',length=100,depth=12,gapU=8,gapV=6))
click(desc='沿列下一格');expect('101');click(desc='回到开头');expect('00   A · 长列')
start(dataset='empty',detail=False,config=dict(density='Custom',length=360,depth=64,gapU=40,gapV=40,peak=120,base=48,center=64))
expect('新建 CATEGORY')
print('PASS empty, 100/1, U99 and tail stay finite in extreme geometry',flush=True)
mutate_display({},save=True)

adb('shell','settings','put','secure','show_ime_with_hard_keyboard','0')
adb('shell','settings','delete','secure','stylus_handwriting_enabled')
