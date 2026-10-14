from pathlib import Path
import subprocess,sys,time
out=Path(__file__).resolve().parent
until=time.monotonic()+600
while 'All recording assertions passed' not in (out/'录屏采集结果.txt').read_text():
 if time.monotonic()>until:raise RuntimeError('Recording did not finish')
 time.sleep(1)
import 采集工具 as c
c.adb('shell','settings','put','secure','show_ime_with_hard_keyboard','1')
c.adb('shell','settings','put','secure','stylus_handwriting_enabled','0')
c.start(8,0,dataset='adjust',adjust=True);c.click(desc='移动到第 N 项');c.replace_field(0,'99');c.shot('08-position-keyboard-v1.png');c.back();c.click(label='取消移动')
print('Final keyboard recapture passed',flush=True)
for script,log in [('阶段04后半回归.py','阶段04后半回归运行日志.txt'),('阶段03交互回归.py','阶段03回归运行日志.txt')]:
 print('START '+script,flush=True)
 with (out/log).open('w') as f:r=subprocess.run([sys.executable,str(out/script)],stdout=f,stderr=subprocess.STDOUT)
 print('END '+script+' exit='+str(r.returncode),flush=True)
 if r.returncode:sys.exit(r.returncode)
print('REMAINING VALIDATION PASSED',flush=True)
