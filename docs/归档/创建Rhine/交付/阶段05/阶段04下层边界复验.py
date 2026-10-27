import 采集工具 as c
import time
c.start(1,0,dataset='section-uneven');c.click(desc='进入 Section 层')
c.click(desc='删除焦点对象');c.expect('全部 Section');c.click(label='确认删除');time.sleep(1)
c.expect('00   设计评审');c.expect('01 TASKS')
c.click(desc='沿列下一格');c.click(desc='沿列下一格');assert c.nodes(label='02')
c.adb('shell','input','swipe',650,420,2100,846,500);time.sleep(2);assert c.nodes(label='02')
print('PASS Section longest column deletion shrinks U101 to U2',flush=True)
c.click(desc='回到开头');c.click(desc='删除焦点对象');c.click(label='确认删除');time.sleep(1)
c.expect('00 TASKS');c.expect('新建 TASK');c.click(desc='新建 TASK');c.click(label='取消');c.expect('00 TASKS')
print('PASS Section layer delete-all returns unique empty start; cancel creates nothing',flush=True)
