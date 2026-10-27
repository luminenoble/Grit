from 采集工具 import *

def snap(name,case):
    shot(name)
    p=OUT/'截图环境.json';d=json.loads(p.read_text());d[name].update({'version':'6.0.7-rhine-stage07-v1','case':case,'display':inspect_config(),'ui':texts()});p.write_text(json.dumps(d,ensure_ascii=False,indent=2))

adb('shell','settings','put','secure','show_ime_with_hard_keyboard','1')
adb('shell','settings','put','secure','stylus_handwriting_enabled','0')
import os
if not os.getenv('RHINE_MEDIA_RESUME'):
    start(8,0,dataset='adjust',detail=False,config={});snap('01-category-default-v1.png','adjust / CATEGORY / PREVIEW / U8 V0 / Task8, ID1007; center peak')
    click(desc='沿列下一格');snap('02-center-peak-v1.png','adjust / CATEGORY / PREVIEW / U9 V0 / Task9')
    click(desc='沿列上一格');click(desc='切换预览/详情模式');click(desc='进入 Section 层');click(desc='沿列下一格');snap('03-section-default-v1.png','adjust / SECTION / DETAIL / U1 Task8; 12 sections')
    click(desc='编辑 Section');snap('04-section-edit-v1.png','Section1 edit; unchanged draft')
    replace_field(0,'Stage07Section');snap('05-section-ime-v1.png','Section name input with actual full Gboard, Save/Cancel')
    back();click(label='取消');click(desc='返回类别层');expect('08   Task 8')
    start(99,1,dataset='uneven',config={});snap('06-u99-short-v1.png','uneven 100/1/1 / CATEGORY / DETAIL / U99 V1')
    click(desc='尾部新增 TASK');replace_field(0,'Stage07Short');back();snap('07-create-task-v1.png','U99 short-column append draft; no intermediate objects')
    click(label='保存');expect('02   Stage07Short');state=inspect();assert len([t for t in state['tasks'] if t['parent']==2])==2;snap('08-short-second-v1.png','one append / CATEGORY / DETAIL / U2 V1')
    start(101,0,dataset='uneven',config={});snap('09-tail-v1.png','CATEGORY / DETAIL / U101 V0; unique tail')
if os.getenv('RHINE_MEDIA_RESUME') != 'live-search':
    start(0,0,dataset='empty',config={});snap('10-empty-v1.png','0 categories / CATEGORY / DETAIL / U0 V0')
    start(0,0,dataset='one-empty',config={});snap('11-one-empty-v1.png','1 category, 0 Task / CATEGORY / DETAIL / U0 V0')
    start(8,0,dataset='adjust',adjust=True,config={});snap('12-adjust-v1.png','adjust / CATEGORY / ADJUST / U8 V0')
    click(desc='移动到第 N 项');replace_field(0,'2');snap('13-sort-ime-v1.png','Task8 to2 / real Gboard / numeric input, Save/Cancel')
    back();click(label='确认移动');expect('02   Task 8');assert inspect()['commits']==1;snap('14-sort-result-v1.png','stable ID1007 / U2 V0 / one commit')
else:
    click(label='关闭搜索')
click(desc='搜索 Task 标题');replace_field(0,'Task%s2');back();expect('已完成');snap('15-search-v1.png','Task 2 title search; repeated names across categories and completed results')
click(label='关闭搜索');click(label='SETTING ↗');snap('16-settings-v1.png','same Task8 / display draft Standard / preview')
click(label='取消')
adb('shell','wm','size','1080x1920');time.sleep(2);snap('17-standard-16x9-v1.png','1920x1080 / 420dpi / Task8 U2 V0 / orientation recreation')
adb('shell','wm','size','720x1280');adb('shell','wm','density','320');time.sleep(2);snap('18-small-window-v1.png','1280x720 / 320dpi / 640x360dp / Task8 U2 V0')
adb('shell','settings','put','system','font_scale','1.6');time.sleep(2);snap('19-small-large-font-v1.png','small window fontScale1.6 / Task8 U2 V0')
adb('shell','wm','size','reset');adb('shell','wm','density','reset');time.sleep(2);snap('20-large-font-20x9-v1.png','2400x1080 / 420dpi / fontScale1.6 / Task8 U2 V0')
adb('shell','settings','put','system','font_scale','1.0');time.sleep(2)
for overlay,name in [('tall','21-notch-v1.png'),('hole','22-hole-v1.png')]:
    key='com.android.internal.display.cutout.emulation.'+overlay
    adb('shell','cmd','overlay','enable',key);time.sleep(2);snap(name,overlay+' cutout overlay / Task8')
    (OUT/'logs'/name.replace('.png','-display.txt')).write_text(adb('shell','dumpsys','display'))
    adb('shell','cmd','overlay','disable',key);time.sleep(1)
adb('shell','input','swipe',1400,1078,1400,850,500);time.sleep(.5);snap('23-transient-bars-v1.png','transient bars / safe footer buttons')
adb('shell','input','keyevent','3');time.sleep(2);adb('shell','am','start','-n',COMPONENT);time.sleep(2);expect('02   Task 8');snap('24-background-restored-v1.png','background/resume / CATEGORY / ADJUST / stable focus ID1007')

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
