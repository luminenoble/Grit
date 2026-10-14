from 采集工具 import *
adb('shell','settings','put','secure','show_ime_with_hard_keyboard','1')
adb('shell','settings','put','secure','stylus_handwriting_enabled','0')
start(8,0,dataset='adjust',detail=False,config={})
def browse():click(desc='沿列下一格');click(desc='沿列上一格');click(desc='切换预览/详情模式')
def enter_task():click(desc='进入 Section 层');click(desc='沿列下一格')
def edit_section():
    click(desc='编辑 Section');replace_field(0,'Stage07Section');back();click(label='保存');expect('Stage07Section')
def create_section():
    # Only 12 existing records: navigate to the unique tail using real HUD steps.
    for _ in range(12):click(desc='沿列下一格')
    click(desc='尾部新增 SECTION');click(label='保存');expect('13   unname');click(desc='返回类别层');expect('08   Task 8')
def sort_task():
    click(desc='切换预览/详情模式');click(desc='移动到第 N 项');replace_field(0,'2');back();click(label='确认移动');expect('02   Task 8')
def search_task():
    click(desc='搜索 Task 标题');replace_field(0,'Task%s8');back();click(label='Task 8');expect('02   Task 8');expect('MODE  PREVIEW')
def configure():
    click(label='SETTING ↗');click(label='紧凑');click(label='保存');expect('已保存');click(label='取消');assert inspect_config()['density']=='Compact'
    click(desc='沿列下一格');click(desc='沿列上一格');expect('02   Task 8');expect('/  12')
record('25-complete-flow-v1.mp4',[browse,enter_task,edit_section,create_section,sort_task,search_task,configure])
mutate_display({},save=True)
print('PASS final screenshot set and uninterrupted workflow',flush=True)
