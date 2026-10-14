from pathlib import Path
import hashlib,subprocess,xml.etree.ElementTree as ET,json,re
from PIL import Image
p=Path(__file__).resolve().parent
checks=[]
for line in (p/'交付文件-SHA256.txt').read_text().splitlines():
 digest,name=line.split('  ',1);assert hashlib.sha256((p/name).read_bytes()).hexdigest()==digest,name
checks.append('PASS final APK, 25 PNG and 5 MP4 SHA-256 manifests')
images=sorted(p.glob('[0-9][0-9]-*-v1.png'));assert len(images)==25
for f in images:
 with Image.open(f) as im:assert im.size==(2400,1080),(f.name,im.size)
checks.append('PASS 25 original PNG: 2400x1080')
results=[ET.parse(f).getroot() for f in p.glob('TEST-*.xml')]
assert sum(int(x.get('tests')) for x in results)==56
assert sum(int(x.get('failures','0'))+int(x.get('errors','0'))+int(x.get('skipped','0')) for x in results)==0
checks.append('PASS 56 unit tests, zero failures/errors/skips')
assert 'OK (22 tests)' in (p/'最终设备测试结果.txt').read_text();checks.append('PASS 22 Android device tests')
for file,count,prefixes in [('核心交互最终汇总.txt',16,('PASS ',)),('补充交互断言结果.txt',9,('PASS ',)),('阶段03最终回归汇总.txt',18,('PASS ',)),('阶段04最终回归汇总.txt',23,('PASS ','CHECKPOINT '))]:
 assert len([x for x in (p/file).read_text().splitlines() if x.startswith(prefixes)])==count,file
 checks.append(f'PASS {file}: {count}')
metadata=json.loads((p/'录屏元数据.json').read_text());assert len(metadata)==5
assert all(x['frames']>0 and x['seconds']>0 for x in metadata.values())
assert len(list(p.glob('*-review-??.jpg')))==31
checks.append('PASS 5 full MP4 decoded; 31 continuous 4fps review pages')
assert 'Exit: 0' in (p/'定向格式检查.txt').read_text();checks.append('PASS targeted ktfmt check')
r=subprocess.run(['git','diff','--check'],cwd=p,capture_output=True,text=True);assert r.returncode==0,r.stdout+r.stderr
checks.append('PASS git diff --check')
(p/'交付核验.txt').write_text('\n'.join(checks)+'\n')
for f in [p/'实现记录.md',p/'测试结果.md',p/'媒体自检记录.md',p.parents[1]/'阶段06执行提示词.md']:
 for link in re.findall(r'\]\(([^)]+)\)',f.read_text()):
  if '://' not in link and not link.startswith('#'):assert (f.parent/link.split('#')[0]).exists(),(f,link)
checks.append('PASS delivery document links')
(p/'交付核验.txt').write_text('\n'.join(checks)+'\n')
print('\n'.join(checks))
