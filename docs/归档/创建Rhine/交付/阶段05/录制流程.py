from 采集工具 import *
import sys
subprocess.run([sys.executable,str(OUT/"准备最终媒体.py")],check=True)

def task_drag():
    pickup();time.sleep(1);drag_to(1330,511);time.sleep(1);release(1330,511);expect('02   Task 8')
start(8,0,dataset='adjust',adjust=True)
record('26-task-eight-to-two-v1.mp4',[task_drag])

def column_drag():
    pickup(630,462);time.sleep(1);drag_to(1133,183,start=(630,462));time.sleep(1);release(1133,183)
start(0,0,dataset='adjust',adjust=True)
record('27-category-column-v1.mp4',[column_drag])

start(1,0,dataset='adjust');click(desc='进入 Section 层')
for _ in range(8):click(desc='沿列下一格')
click(desc='切换预览/详情模式')
def section_drag():
    pickup();time.sleep(1);drag_to(1330,511);time.sleep(1);release(1330,511);expect('02   Section 8')
record('28-section-and-sibling-v1.mp4',[section_drag,lambda:click(desc='回到开头'),column_drag])

start(95,0,dataset='uneven',adjust=True)
def edge_drag():
    pickup();drag_to(20,126);time.sleep(3);release(20,126);expect('100   A · Task 95')
record('29-finite-edge-scroll-v1.mp4',[edge_drag])

start(1,0,dataset='adjust');click(desc='进入 Section 层')
def search_input():
    click(desc='搜索 Task 标题');replace_field(0,'Task%s2');back();expect('已完成')
def select():
    click(label='Task 2');expect('CATEGORY LAYER');expect('MODE  PREVIEW');expect('02   Task 2')
record('30-search-from-sections-v1.mp4',[search_input,select])
print('All recording assertions passed',flush=True)
