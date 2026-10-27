import 采集工具 as c
from pathlib import Path
import time
import sys
checks=[]
if "--draft-only" in sys.argv:
    checks=Path(__file__).with_name("提交与草稿断言结果.txt").read_text().splitlines()
    checks=[x.removeprefix("PASS ") for x in checks]
def done(s):
    checks.append(s);print('PASS '+s,flush=True)
    Path(__file__).with_name('提交与草稿断言结果.txt').write_text('\n'.join('PASS '+x for x in checks)+'\n')
def submit_and_check(title):
    c.replace_field(0,title);c.back();c.tap_node(c.nodes(label='保存')[0]);time.sleep(.15)
    tree=c.tree();fields=[n for n in tree.iter('node') if n.get('class')=='android.widget.EditText']
    assert fields and all(n.get('enabled')=='false' for n in fields),[(n.get('text'),n.get('enabled')) for n in fields]
    assert any('保存中' in n.get('text','') for n in tree.iter('node'))
    c.adb('shell','input','keyevent','4');time.sleep(7)

if '--draft-only' not in sys.argv:
    c.start(0,0,dataset='empty',delay=7000);c.click(desc='新建 CATEGORY');submit_and_check('BusyCategory')
    c.expect('00   BusyCategory');c.expect('01 CATEGORIES');c.expect('00 TASKS');done('Category saving disables fields and back; commits exactly one parent at zero')
    c.start(99,1,dataset='uneven',delay=7000);c.click(desc='尾部新增 TASK');submit_and_check('BusyTask')
    c.expect('02   BusyTask');c.expect('103 TASKS');done('Task saving disables fields and back; exactly one item at U2')


def open_empty_task():
    c.start(3,1);c.click(desc='进入 Section 层');c.click(desc='编辑 TASK')
    for _ in range(8):
        if c.nodes(label='Add section'): break
        c.scroll()
    c.expect('排序待接入');c.click(label='Reorder sections');assert not c.nodes(label='Finish reordering')

def add_draft():
    c.click(label='Add section');c.expect('unname');c.click(label='保存')

open_empty_task();add_draft();c.click(label='取消');c.expect('未保存的修改');c.click(label='放弃')
c.expect('/  00');c.expect('22 SECTIONS');done('embedded Section add stays draft; discard preserves empty Task and sorting remains disabled')
open_empty_task();add_draft();c.click(label='保存');time.sleep(1);c.expect('/  01');c.expect('23 SECTIONS')
c.click(desc='沿列下一格');c.expect('01   unname');done('whole Task save commits embedded Section through the existing transaction')
print(f'{len(checks)} submission/draft checks passed',flush=True)
