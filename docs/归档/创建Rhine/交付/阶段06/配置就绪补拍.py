from 采集工具 import *
start(u=8,v=0,dataset='adjust',detail=False,config=dict(placeholderNames=True))
shot('09-placeholder-names-v1.png')
start(u=8,v=0,dataset='adjust',detail=False,config=dict(scrollFocusedTitle=True))
mutate('long-title',taskId=1007);time.sleep(2);shot('18-focused-long-title-v1.png')
start(u=8,v=0,dataset='adjust',detail=True,config=dict(categoryNumbers=False,taskNumbers=False,sectionNumbers=False))
click(desc='进入 Section 层');click(desc='沿列下一格');shot('27-section-hidden-numbers-v1.png')
mutate_display({},save=True)
print('Configuration applied and captures passed',flush=True)
