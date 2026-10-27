from 采集工具 import *

start(u=8,v=0,dataset='adjust',detail=False,config={})
before=inspect_display()['business']
for c in [{"density":"Compact","targetRows":16,"targetColumns":4},{"density":"Custom","length":100,"depth":12,"gapU":8,"gapV":6,"base":8,"center":8,"peak":0},{"density":"Custom","length":360,"depth":64,"gapU":40,"gapV":40,"base":48,"center":64,"peak":120}, {"topSp":20,"sideSp":20,"categoryNumbers":False,"taskNumbers":False,"sectionNumbers":False}, {}]:
    mutate_display(c)
    assert inspect_display()['business']==before
    expect('Task 8')
print('PASS configurations keep every business field and focus ID',flush=True)
click(label='SETTING ↗');click(label='紧凑');click(label='取消');expect('08   Task 8')
assert json.loads(inspect_display()['display'])['density']=='Standard'
print('PASS cancel draft does not save',flush=True)
click(label='SETTING ↗');click(label='宽松');click(label='保存');expect('已保存');click(label='取消');expect('08   Task 8')
assert json.loads(inspect_display()['display'])['density']=='Loose'
start(u=8,v=0,dataset='adjust',detail=False)
assert json.loads(inspect_display()['display'])['density']=='Loose'
print('PASS UI save and process restart persist',flush=True)
click(label='SETTING ↗');click(label='恢复默认');click(label='保存');click(label='取消')
assert json.loads(inspect_display()['display'])['density']=='Standard'
print('PASS UI reset and save',flush=True)
click(desc='切换预览/详情模式');click(desc='进入 Section 层');expect('SECTION LAYER')
mutate_display(dict(density='Compact',targetRows=16,targetColumns=4))
click(desc='返回类别层');expect('08   Task 8')
print('PASS lower configuration preserves upper return snapshot',flush=True)
click(desc='切换预览/详情模式');expect('MODE  ADJUST');click(desc='移动到第 N 项')
mutate_display(dict(topSp=12));assert not nodes(label='确认移动');expect('08   Task 8')
print('PASS configuration closes uncommitted position panel',flush=True)
click(desc='搜索 Task 标题');replace_field(0,'Task%s2');mutate_display({});assert not nodes(label='关闭搜索');expect('08   Task 8')
print('PASS configuration cancels search and stale input',flush=True)
back()
