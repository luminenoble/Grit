from 采集工具 import *
import shutil
old=OUT/'diagnostics'/'date-before-task-integration';old.mkdir(parents=True,exist_ok=True)
for name in ['30-section-date-keyboard-v1.png','31-section-date-saved-v1.png']:
    shutil.copyfile(OUT/name,old/name)
adb('shell','settings','put','secure','show_ime_with_hard_keyboard','1');adb('shell','settings','put','secure','stylus_handwriting_enabled','0')
start(2,1,dataset='detail',config={});enter();click(desc='沿列下一格');click(desc='编辑 Section')
n=nodes(klass='android.widget.ScrollView')[0];x1,y1,x2,y2=map(int,re.findall(r'\d+',n.get('bounds')))
scroll(x1+4,min(y2-60,780),max(y1+40,330))
fs=nodes(klass='android.widget.EditText');index=next(i for i,n in enumerate(fs) if n.get('text')=='2026-01-02')
replace_field(index,'2099-01-01');expect('2099-01-01');click(label='保存');expect('不能晚于今天')
fs=nodes(klass='android.widget.EditText');index=next(i for i,n in enumerate(fs) if n.get('text')=='2099-01-01')
replace_field(index,'2026-01-03');expect('2026-01-03');time.sleep(1);expect('保存');expect('取消')
n=nodes(klass='android.widget.ScrollView')[0];x1,y1,x2,y2=map(int,re.findall(r'\d+',n.get('bounds')))
scroll(x1+4,max(y1+20,min(240,y2-30)),max(y1+20,min(290,y2-20)))
shot('30-section-date-keyboard-v1.png');click(label='保存');expect('DATE 2026-01-03');shot('31-section-date-saved-v1.png')
print('PASS final APK future date rejects, full IME label/value/Save/Cancel visible, past date saves',flush=True)
