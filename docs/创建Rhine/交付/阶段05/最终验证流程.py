from pathlib import Path
import subprocess,sys
out=Path(__file__).resolve().parent
steps=[('交互回归.py','交互回归运行日志.txt'),('补充交互回归.py','补充交互运行日志.txt'),('阶段04尾插删除回归.py','阶段04尾插删除运行日志.txt'),('阶段04交互回归.py','阶段04回归运行日志.txt'),('阶段03交互回归.py','阶段03回归运行日志.txt'),('录制流程.py','录屏采集结果.txt')]
if '--resume' in sys.argv:steps=steps[1:]
for script,log in steps:
    print('START '+script,flush=True)
    with (out/log).open('w') as f:r=subprocess.run([sys.executable,str(out/script)],stdout=f,stderr=subprocess.STDOUT)
    print(f'END {script} exit={r.returncode}',flush=True)
    if r.returncode:sys.exit(r.returncode)
print('FINAL PIPELINE PASSED',flush=True)
