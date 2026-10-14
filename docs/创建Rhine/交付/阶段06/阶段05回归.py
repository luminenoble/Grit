from 采集工具 import *
import traceback,os
checks=[];failures=[]
def check(name,fn):
    selected=os.getenv('RHINE_TEST_FILTER')
    if selected and not any(x in name for x in selected.split('|')):return
    try:
        fn();checks.append(name);print('PASS '+name,flush=True)
    except Exception:
        failures.append(name);print('FAIL '+name+'\n'+traceback.format_exc(),flush=True)
        try: (OUT/('failure-'+str(len(failures))+'.png')).write_bytes(adb('exec-out','screencap','-p',binary=True));motion('UP',816,360)
        except Exception:pass
    (OUT/('交互重试断言结果.txt' if os.getenv('RHINE_TEST_FILTER') else '交互断言结果.txt')).write_text('\n'.join('PASS '+n for n in checks)+'\n'+'\n'.join('FAIL '+n for n in failures))
def task(state,id):return next(t for t in state['tasks'] if t['id']==id)
def ids(state,parent):return [t['id'] for t in sorted(state['tasks'],key=lambda t:t['order']) if t['parent']==parent]
def no_move(state):assert state['attempts']==state['commits']==0,state

def content_sort():
    start(8,0,dataset='adjust',adjust=True);pickup();drag_to(1330,511);release(1330,511);expect('02   Task 8')
    state=inspect();assert state['attempts']==state['commits']==1;assert ids(state,1)[:8]==[1000,1007,1001,1002,1003,1004,1005,1006]
    assert task(state,1007)['parent']==1
    click(desc='移动到第 N 项');replace_field(0,'8');back();click(label='确认移动');expect('08   Task 8')
    state=inspect();assert ids(state,1)==list(range(1000,1012));assert state['commits']==2
check('Task 8→2, middle shift, reverse by ordinal, stable parent and one commit',content_sort)

def no_op_cancel():
    start(8,0,dataset='adjust',adjust=True);pickup();release(816,360);no_move(inspect())
    pickup();drag_to(1330,511);adb('shell','input','keyevent','4');release(1330,511);expect('08   Task 8');no_move(inspect())
    click(desc='移动到第 N 项');replace_field(0,'2');back();click(label='取消移动');no_move(inspect())
check('original release, Android back and ordinal cancel never call move',no_op_cancel)

def gates():
    for mode in [False,True]:
        start(8,0,dataset='adjust',detail=mode);pickup();release(816,360);expect('CATEGORY LAYER');no_move(inspect())
    start(8,2,dataset='adjust',adjust=True);pickup();release(816,360);no_move(inspect())
    start(0,5,dataset='adjust',adjust=True);pickup();release(816,360);no_move(inspect())
    start(8,0,dataset='adjust',adjust=True);pickup(20,300);release(20,300);no_move(inspect())
check('preview/detail long press, placeholder, unique add column and decoration cannot sort',gates)

def noncenter():
    start(8,1,dataset='adjust',adjust=True);adb('shell','input','tap',313,885);time.sleep(1);expect('CATEGORY 01');assert not nodes(label='移动到第 N 项');no_move(inspect())
check('noncenter first tap only centers, never opens position form',noncenter)

def parent_sort():
    start(0,0,dataset='adjust',adjust=True);pickup(630,462);drag_to(1133,183,start=(630,462));release(1133,183)
    state=inspect();assert [c['id'] for c in state['categories']]==[2,1,3,4,5];assert state['commits']==1
    assert ids(state,1)==list(range(1000,1012));expect('CATEGORY 02');expect('00   学习')
check('Category zero moves whole column, excludes zero from content ordering',parent_sort)

def sections():
    start(1,0,dataset='adjust');click(desc='进入 Section 层')
    for _ in range(8):click(desc='沿列下一格')
    click(desc='切换预览/详情模式');pickup();drag_to(1330,511);release(1330,511);expect('02   Section 8')
    state=inspect();assert [s['id'] for s in task(state,1000)['sections']][:8]==[100001,100008,100002,100003,100004,100005,100006,100007]
    click(desc='回到开头');pickup(630,462);drag_to(1133,183,start=(630,462));release(1133,183)
    state=inspect();assert ids(state,1)[:3]==[1001,1000,1002];assert state['commits']==2;expect('00   Task 1')
    click(desc='返回类别层');expect('02   Task 1')
check('Section 8→2 and sibling Task zero along V; parent return follows ID',sections)

def invalid_retry():
    start(8,0,dataset='adjust',adjust=True,fail_move=True,delay=1500);click(desc='移动到第 N 项')
    for invalid in ['0','-1','99','1.2','abc']:
        replace_field(0,invalid);back();click(label='确认移动');expect('请输入 1–12 的整数')
    no_move(inspect());replace_field(0,'2');back();click(label='确认移动');time.sleep(2);expect('模拟移动失败')
    state=inspect();assert state['attempts']==1 and state['commits']==0
    click(label='确认移动');time.sleep(2);expect('02   Task 8');state=inspect();assert state['commits']==1
check('invalid ordinals rejected without call; failure retains retry context',invalid_retry)

def double_submit():
    start(8,0,dataset='adjust',adjust=True,delay=2000);click(desc='移动到第 N 项');replace_field(0,'2');back()
    n=nodes(label='确认移动')[0];x1,y1,x2,y2=map(int,re.findall(r'\d+',n.get('bounds')));x=(x1+x2)//2;y=(y1+y2)//2
    adb('shell',f'input tap {x} {y}; input tap {x} {y}; input tap {x} {y}; input keyevent 4');time.sleep(3)
    state=inspect();assert state['attempts']==state['commits']==1;expect('02   Task 8')
check('rapid confirmation and back during commit submit exactly once',double_submit)

def stale_move():
    for change in ['reorder-task','move','delete-task','delete-category','import-reused']:
        start(8,1,dataset='adjust',adjust=True);click(desc='移动到第 N 项');mutate(change,taskId=2007)
        assert not nodes(label='确认移动'),change;no_move(inspect());expect('CATEGORY LAYER')
check('ordinal invalidates on reorder, external move/delete, parent deletion and reused import',stale_move)

def drag_invalidates():
    for change in ['reorder-task','import-reused']:
        start(8,1,dataset='adjust',adjust=True);pickup();mutate(change,taskId=2007);release(816,360);no_move(inspect())
    start(8,0,dataset='adjust',adjust=True);pickup()
    try:adb('shell','wm','size','1080x2000');time.sleep(1);release(816,360);no_move(inspect())
    finally:adb('shell','wm','size','reset')
check('active drag cancels on mutation/import/size change without a write',drag_invalidates)

def rename_drag():
    start(8,1,dataset='adjust',adjust=True);click(desc='移动到第 N 项');mutate('rename',taskId=2007)
    replace_field(0,'2');back();click(label='确认移动');expect('02   外部修改');state=inspect();assert task(state,2007)['order']==1
check('rename/color update preserves stable move identity and current title',rename_drag)

def search_basics():
    start(0,0,dataset='search');click(desc='搜索 Task 标题');replace_field(0,'aLpHa');back();expect('Alpha 设计评审 1');expect('已完成')
    click(label='下一页');expect('第 2 页');expect('Alpha 设计评审 52');click(label='上一页');expect('第 1 页')
    replace_field(0,'zzzz');back();expect('没有匹配的 Task');replace_field(0,'literal');back();expect('literal')
    click(label='关闭搜索');expect('00   学习')
check('case-insensitive search, completed, pages, empty and literal-title results',search_basics)

def search_retry_latest():
    start(0,0,dataset='search',fail_search=True);click(desc='搜索 Task 标题');replace_field(0,'alpha');back();expect('搜索失败');click(label='重试搜索');expect('Alpha 设计评审 1')
    start(0,0,dataset='search',search_delay=1800);click(desc='搜索 Task 标题');replace_field(0,'alpha');replace_field(0,'NotThere');back();time.sleep(2);expect('没有匹配的 Task');assert not nodes(label='Alpha 设计评审 1')
check('search failure retries; rapid new input rejects delayed old results',search_retry_latest)

def search_stale():
    start(0,0,dataset='adjust');click(desc='搜索 Task 标题');replace_field(0,'Task%s8');back();mutate('move',taskId=1007)
    click(label='Task 8');expect('CATEGORY 03');expect('02   Task 8');expect('MODE  PREVIEW')
    start(0,0,dataset='adjust');click(desc='搜索 Task 标题');replace_field(0,'Task%s8');back();mutate('delete-task',taskId=1007)
    click(label='Task 8');time.sleep(5.5);expect('结果已删除');click(label='关闭搜索');expect('CATEGORY LAYER')
check('selected stale result follows moved ID; deleted result leaves valid scene',search_stale)

def search_section_import_back():
    start(1,0,dataset='adjust');click(desc='进入 Section 层');click(desc='搜索 Task 标题');replace_field(0,'Task%s8');back();click(label='Task 8');expect('CATEGORY LAYER');expect('MODE  PREVIEW');assert not nodes(desc='返回类别层')
    start(1,0,dataset='adjust',search_delay=1500);click(desc='进入 Section 层');click(desc='搜索 Task 标题');replace_field(0,'Task');back();mutate('import-reused');time.sleep(2);expect('CATEGORY LAYER');expect('00   学习');assert not nodes(label='关闭搜索')
    click(desc='搜索 Task 标题');replace_field(0,'Task');back();click(label='关闭搜索');time.sleep(2);assert not nodes(label='Task 1')
check('second-layer search returns upper preview, clears parent; import/back discard pending query',search_section_import_back)

def finite_edge():
    start(99,0,dataset='uneven',adjust=True);pickup();drag_to(20,126);time.sleep(3);release(20,126);state=inspect();assert task(state,1098)['order']==99;assert len(ids(state,1))==100;expect('100   A · Task 99')
check('edge scrolling clamps to last real item, never U101 placeholder',finite_edge)

print(f'{len(checks)} passed, {len(failures)} failed',flush=True)
if failures:raise SystemExit(1)
