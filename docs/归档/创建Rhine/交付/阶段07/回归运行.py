from pathlib import Path
import subprocess,time,json
out=Path(__file__).resolve().parent
scripts=['交互回归.py','补充交互.py','阶段03交互回归.py','阶段04尾插删除回归.py','阶段05回归.py']
results=[]
ADB='/mnt/d/professionalTools/AndroidStorage/sdk/platform-tools/adb.exe'
for name in scripts:
    subprocess.run([ADB,'shell','settings','put','secure','show_ime_with_hard_keyboard','1'],check=True)
    subprocess.run([ADB,'shell','settings','put','secure','stylus_handwriting_enabled','0'],check=True)
    begin=time.monotonic()
    with (out/'logs'/name.replace('.py','-run.txt')).open('w') as log:
        code=subprocess.call(['python3',str(out/name)],stdout=log,stderr=subprocess.STDOUT)
    results.append({'script':name,'exit':code,'seconds':time.monotonic()-begin})
    (out/'logs'/'ui-regression-summary.json').write_text(json.dumps(results,ensure_ascii=False,indent=2))
    print(results[-1],flush=True)
raise SystemExit(any(x['exit'] for x in results))
