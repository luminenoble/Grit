"""Stage04 evidence: actual Android input/screencap against isolated in-memory data."""
import 采集工具 as c
import time
c.shot=lambda name: print("CHECKPOINT "+name,flush=True)


def new_task(title):
    c.click(desc='尾部新增 TASK');c.replace_field(0,title);c.back();c.click(label='保存');time.sleep(1)

def to_section_tail():
    # Real U drag/fling; the shared bound is 101 for the 100/1 fixture.
    for _ in range(20):
        if c.nodes(label="101"): break
        c.adb('shell','input','swipe',650,420,2100,846,65);time.sleep(1.6)
    time.sleep(1)
    assert c.nodes(label='101'), 'Exact U101 readout missing'
    c.click(desc='沿列上一格');c.click(desc='沿列上一格');assert c.nodes(label='99'), 'Exact U99 readout missing'


def run():
    c.start(0,0,dataset='empty');c.shot('01-empty-start-v1.png')
    c.click(desc='新建 CATEGORY');c.shot('02-category-form-v1.png')
    c.replace_field(0,'NewCategory');c.shot('03-category-keyboard-v1.png');c.back()
    c.click(label='保存');time.sleep(1);c.expect('00   NewCategory');c.shot('04-category-zero-v1.png')
    c.click(desc='沿列下一格');new_task('FirstTask');c.expect('01   FirstTask');c.shot('05-first-task-v1.png')

    c.start(99,1,dataset='uneven',fail=True);c.expect('/  01');c.shot('06-u99-short-before-v1.png')
    c.click(desc='尾部新增 TASK');c.replace_field(0,'ShortSecond');c.back();c.click(label='保存')
    c.expect('模拟保存失败');c.expect('ShortSecond');c.shot('07-create-failed-draft-v1.png')
    c.click(label='保存');time.sleep(1);c.expect('02   ShortSecond');c.expect('/  02');c.shot('08-short-second-after-v1.png')

    c.start(1,0,dataset='section-uneven');c.click(desc='进入 Section 层');to_section_tail()
    c.click(desc='下一列');c.expect('/  01');c.shot('09-section-u99-short-before-v1.png')
    c.click(desc='尾部新增 SECTION');c.expect('unname');c.shot('10-section-create-v1.png')
    c.click(label='保存');time.sleep(1);c.expect('02   unname');c.expect('/  02');c.shot('11-section-second-after-v1.png')
    c.click(desc='下一列');c.click(desc='新建 TASK');c.replace_field(0,'NewSibling');c.back();c.shot('12-sibling-form-v1.png')
    c.click(label='保存');time.sleep(1);c.expect('00   NewSibling');c.expect('/  00');c.shot('13-sibling-zero-v1.png')
    c.click(desc='沿列下一格');c.click(desc='尾部新增 SECTION');c.click(label='保存');time.sleep(1)
    c.expect('01   unname');c.click(desc='删除焦点对象');c.shot('14-section-delete-confirm-v1.png')
    c.click(label='确认删除');time.sleep(1);c.expect('00   NewSibling');c.shot('15-section-deleted-zero-v1.png')

    c.start(101,0,dataset='uneven',fail_delete=True);c.shot('16-longest-before-v1.png')
    c.click(desc='删除焦点对象');c.expect('全部 Task、Section');c.shot('17-category-cascade-confirm-v1.png')
    c.click(label='确认删除');time.sleep(1);c.expect('模拟删除失败');c.shot('18-delete-failed-context-v1.png')
    c.click(label='确认删除');time.sleep(1);c.expect('02 CATEGORIES');c.expect('00   B');c.shot('19-longest-removed-v1.png')
    c.click(desc='沿列下一格');c.click(desc='沿列下一格');c.expect('02');c.shot('20-shrunk-tail-v1.png')

    c.start(0,0,dataset='one-empty');c.click(desc='删除焦点对象');c.click(label='确认删除');time.sleep(1)
    c.expect('00 CATEGORIES');c.shot('21-deleted-all-empty-v1.png')
    print('All screenshot assertions passed',flush=True)

if __name__=='__main__': run()
