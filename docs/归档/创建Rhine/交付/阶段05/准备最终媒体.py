from pathlib import Path
import hashlib,shutil,subprocess,sys
out=Path(__file__).resolve().parent
root=out.parents[3]
adb='/mnt/d/professionalTools/AndroidStorage/sdk/platform-tools/adb.exe'
log=out/'交付核验构建结果.txt'
assert 'BUILD SUCCESSFUL' in log.read_text(), 'Final source build has not completed'
apk=root/'androidApp/build/outputs/grit_rhine/Grit_Rhine-debug.apk'
h=hashlib.sha256(apk.read_bytes()).hexdigest();name=f'Grit_Rhine-stage05-v1-{h[:8]}.apk'
shutil.copy2(apk,out/name);(out/'APK-SHA256.txt').write_text(f'{h}  {name}\n')
shutil.copy2(apk,'/mnt/d/Rhine-stage05.apk')
subprocess.run([adb,'install','-r','D:\\Rhine-stage05.apk'],check=True)
test=root/'androidApp/build/outputs/apk/androidTest/foss/debug/androidApp-foss-debug-androidTest.apk'
shutil.copy2(test,'/mnt/d/Rhine-stage05-test.apk');subprocess.run([adb,'install','-r','D:\\Rhine-stage05-test.apk'],check=True)
classes='com.shub39.grit.RhineAdjustRepositoryTest,com.shub39.grit.RhineMutationRepositoryTest,com.shub39.grit.RhineDetailRepositoryTest,com.shub39.grit.TaskRepositoryTest,com.shub39.grit.RhineBrowseRepositoryTest,com.shub39.grit.RhineTextLayoutTest,com.shub39.grit.RhineAdjustGestureTest'
r=subprocess.check_output([adb,'shell','am','instrument','-w','-e','class',classes,'com.shub39.grit.debug.test/androidx.test.runner.AndroidJUnitRunner'],text=True)
(out/'最终设备测试结果.txt').write_text(r);assert 'OK (22 tests)' in r,r
for p in (root/'androidApp/build/test-results/testFossDebugUnitTest').glob('TEST-*.xml'):shutil.copy2(p,out/p.name)
if '--install-only' in sys.argv:
 print('Installed and verified '+name,flush=True);sys.exit(0)
subprocess.run([adb,'shell','settings','put','secure','show_ime_with_hard_keyboard','1'],check=True)
subprocess.run([adb,'shell','settings','put','secure','stylus_handwriting_enabled','0'],check=True)
for number in [24,25]:
 for p in out.glob(f'{number:02}-*-v1.png'):shutil.copy2(p,out/'诊断'/('051bcf23-'+p.name))
with (out/'截图采集结果.txt').open('w') as f:subprocess.run([sys.executable,str(out/'采集证据.py')],stdout=f,stderr=subprocess.STDOUT,check=True)
print('Final APK and all screenshots: '+name,flush=True)
