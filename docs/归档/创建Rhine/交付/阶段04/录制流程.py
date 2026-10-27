import 采集工具 as c
import time

c.start(99,1,dataset='uneven')
c.record('22-u99-append-and-locate-v1.mp4',[
    lambda: c.click(desc='尾部新增 TASK'),
    lambda: c.replace_field(0,'RecordedSecond'),
    c.back,
    lambda: c.click(label='保存'),
    lambda: c.expect('02   RecordedSecond'),
],seconds=40)
c.start(101,0,dataset='uneven')
c.record('23-delete-longest-and-clamp-v1.mp4',[
    lambda: c.click(desc='删除焦点对象'),
    lambda: c.click(label='确认删除'),
    lambda: c.expect('00   B'),
    lambda: c.click(desc='沿列下一格'),
    lambda: c.click(desc='沿列下一格'),
    lambda: c.adb('shell','input','swipe',650,420,2100,846,70),
    lambda: c.expect('02'),
],seconds=40)
print('Recording assertions passed',flush=True)
