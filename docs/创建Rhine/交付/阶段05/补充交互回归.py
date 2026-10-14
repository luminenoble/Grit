from 采集工具 import *
checks=[]
def done(name):
    checks.append(name);print('PASS '+name,flush=True)
    (OUT/'补充交互断言结果.txt').write_text('\n'.join('PASS '+x for x in checks)+'\n')
start(8,0,dataset='adjust',adjust=True,fail_move=True)
pickup();drag_to(1330,511);release(1330,511);expect('模拟移动失败')
assert nodes(klass='android.widget.EditText')[0].get('text')=='2'
click(label='确认移动');expect('02   Task 8');state=inspect();assert state['attempts']==2 and state['commits']==1
done('failed drag retains target 2 and retry commits once')

result=adb('shell','am','instrument','-w','-e','class','com.shub39.grit.RhineAdjustGestureTest','com.shub39.grit.debug.test/androidx.test.runner.AndroidJUnitRunner')
(OUT/'双指模式取消设备结果.txt').write_text(result)
assert 'OK (1 test)' in result,result
done('real second finger switches mode and cancels held drag without writing')

start(8,0,dataset='adjust',adjust=True,delay=3000)
click(desc='移动到第 N 项');replace_field(0,'2');back();tap_node(nodes(label='确认移动')[0]);mutate('import-reused');time.sleep(3)
state=inspect();assert state['attempts']==1 and state['commits']==0;expect('00   学习')
done('replacement during pending move rejects old transaction including reused IDs')

start(0,0,dataset='search',search_delay=1200);click(desc='搜索 Task 标题');replace_field(0,'alpha');back()
try:
    adb('shell','wm','size','1080x2000');time.sleep(2);expect('Alpha 设计评审 1')
finally:adb('shell','wm','size','reset')
time.sleep(1);click(label='Alpha 设计评审 1');expect('01   Alpha 设计评审 1');expect('MODE  PREVIEW')
done('search query survives viewport resize and lands on stable ID')

start(0,3,dataset='adjust',adjust=True);click(desc='移动到第 N 项');replace_field(0,'1');back();click(label='确认移动');expect('00   空类别')
assert inspect()['categories'][0]['id']==4
done('empty Category zero can reorder among real siblings')

start(3,0,dataset='adjust');click(desc='进入 Section 层');click(desc='切换预览/详情模式');click(desc='移动到第 N 项');replace_field(0,'1');back();click(label='确认移动');expect('00   Task 3')
state=inspect();assert next(t for t in state['tasks'] if t['id']==1002)['order']==0
assert next(t for t in state['tasks'] if t['id']==1002)['sections']==[]
done('empty Task zero reorders sibling Tasks without creating a Section')

start(8,0,dataset='adjust',adjust=True);click(desc='移动到第 N 项')
try:
    adb('shell','wm','size','1080x2000');time.sleep(1);expect('移动到第 N 项');replace_field(0,'2');back()
finally:adb('shell','wm','size','reset')
time.sleep(1);click(label='确认移动');expect('02   Task 8')
done('ordinal dialog survives resize with fixed action controls')
print(f'{len(checks)} supplemental checks passed',flush=True)

start(8,0,dataset='adjust',adjust=True);pickup();drag_to(1700,180);expect('Task 8');release(1700,180)
state=inspect();assert next(t for t in state['tasks'] if t['id']==1007)['parent']==1
assert len([t for t in state['tasks'] if t['parent']==1])==12
assert len([t for t in state['tasks'] if t['parent']==2])==8
done('drag across another screen column stays bound to original Task and Category')

start(1,0,dataset='adjust');click(desc='进入 Section 层');click(desc='切换预览/详情模式');click(desc='沿列下一格');pickup();adb('shell','input','keyevent','4');release(816,360);click(desc='返回类别层');expect('01   Task 1');assert inspect()['commits']==0
done('cancel Section pickup then HUD return preserves original parent snapshot')
print(f'{len(checks)} total supplemental checks passed',flush=True)
