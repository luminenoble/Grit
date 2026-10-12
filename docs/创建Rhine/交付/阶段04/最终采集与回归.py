"""Run against the installed final APK; each step only uses the in-memory debug activity."""
from pathlib import Path
import subprocess
import sys
out=Path(__file__).resolve().parent
for script,log in [
    ('采集证据.py','交付截图采集结果.txt'),
    ('补充日期证据.py','补充日期采集结果.txt'),
    ('录制流程.py','录屏采集结果.txt'),
    ('阶段03交互回归.py','阶段03回归运行日志.txt'),
]:
    print('START '+script,flush=True)
    with (out/log).open('w') as stream:
        result=subprocess.run([sys.executable,str(out/script)],stdout=stream,stderr=subprocess.STDOUT)
    print('END '+script+' exit='+str(result.returncode),flush=True)
    if result.returncode: sys.exit(result.returncode)
print('FINAL EVIDENCE PIPELINE PASSED',flush=True)
