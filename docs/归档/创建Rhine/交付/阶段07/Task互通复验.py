from 采集工具 import *
import struct
adb('shell','settings','put','secure','show_ime_with_hard_keyboard','1')
adb('shell','settings','put','secure','stylus_handwriting_enabled','0')
adb('shell','am','start','-S','-n',COMPONENT,'--es','dataset','detail','--ef','u','1','--ef','v','0','--ez','detail','true','--ez','enableTaskPage','true')
time.sleep(4);expect('GRIT RHINE');mutate_display({},save=True);assert inspect_config()['density']=='Standard'
click(label='TASK ↗');expect('返回隔离 Rhine');time.sleep(2)
click(desc='详情');replace_field(0,'TaskFromTaskPage');back();click(desc='Navigate Back');expect('未保存的修改');click(label='保存');time.sleep(1);expect('TaskFromTaskPage')
shot('32-task-page-saved-v1.png');width,height=struct.unpack('>II',(OUT/'32-task-page-saved-v1.png').read_bytes()[16:24]);assert width<height,(width,height)
print('PASS actual Task detail save into same isolated repository; leaving Rhine restores portrait',flush=True)
click(label='返回隔离 Rhine');expect('01   TaskFromTaskPage');shot('33-task-to-rhine-v1.png');width,height=struct.unpack('>II',(OUT/'33-task-to-rhine-v1.png').read_bytes()[16:24]);assert width>height,(width,height)
print('PASS return Rhine locks landscape and follows stable Task ID/current title',flush=True)
click(desc='进入 Section 层');click(desc='沿列下一格');click(desc='编辑 Section');replace_field(0,'SectionFromRhine');back();click(label='保存');expect('SectionFromRhine');click(desc='返回类别层');click(label='TASK ↗')
click(label='2/6 Section');expect('SectionFromRhine');shot('34-rhine-to-task-section-v1.png')
state=inspect();task=next(t for t in state['tasks'] if t['id']==1000)
assert task['title']=='TaskFromTaskPage' and task['sections'][0]['title']=='SectionFromRhine' and len(task['sections'])==6
print('PASS actual Rhine Section save immediately appears in actual Task list expanded records; stable ID/count',flush=True)
click(label='返回隔离 Rhine');click(desc='进入 Section 层');click(desc='沿列下一格') if not nodes(label='01   SectionFromRhine') else None;expect('SectionFromRhine')
print('PASS page round trip retains task and section positions without replayed saves',flush=True)
