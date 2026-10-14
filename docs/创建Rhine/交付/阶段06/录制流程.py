from 采集工具 import *
start(u=8,v=0,dataset='adjust',detail=False,config={})
mutate_display({},save=True)
def check_saved(density):
    value=json.loads(inspect_display()['display'])
    assert value['density']==density,value
    expect('08   Task 8')
record('23-settings-save-restart-reset-v1.mp4',[
    lambda:click(label='SETTING ↗'),lambda:click(label='紧凑'),lambda:click(label='保存'),
    lambda:click(label='取消'),lambda:check_saved('Compact'),
    lambda:start(u=8,v=0,dataset='adjust',detail=False),lambda:check_saved('Compact'),
    lambda:click(label='SETTING ↗'),lambda:click(label='恢复默认'),lambda:click(label='保存'),
    lambda:click(label='取消'),lambda:check_saved('Standard')])
start(u=8,v=0,dataset='adjust',detail=False,config={})
mutate('long-title',taskId=1007)
record('24-focused-scroll-and-reduced-v1.mp4',[
    lambda:time.sleep(2),lambda:mutate_display(dict(scrollFocusedTitle=True)),lambda:time.sleep(10),
    lambda:mutate_display(dict(scrollFocusedTitle=True,reduceMotion=True)),lambda:time.sleep(3)])
print('Recording assertions passed',flush=True)
start(u=8,v=0,dataset='adjust',detail=True,config=dict(categoryNumbers=False,taskNumbers=False,sectionNumbers=False))
click(desc='进入 Section 层');click(desc='沿列下一格');shot('27-section-hidden-numbers-v1.png')
click(desc='返回类别层');mutate_display({});before=inspect_display()['business'];shot('29-font-eight-seven-v1.png')
mutate_display(dict(topSp=12,sideSp=10));shot('28-font-twelve-ten-v1.png')
assert inspect_display()['business']==before
mutate_display({},save=True)
adb('shell','settings','put','secure','show_ime_with_hard_keyboard','0')
adb('shell','settings','delete','secure','stylus_handwriting_enabled')
