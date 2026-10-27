from pathlib import Path
import subprocess,json,re
from PIL import Image,ImageDraw
out=Path(__file__).resolve().parent
ffmpeg='/tmp/rhine07-media/bin/ffmpeg'
review=out/'review';review.mkdir(exist_ok=True)
metadata={}
for video in sorted(out.glob('*.mp4')):
    probe=subprocess.run([ffmpeg,'-hide_banner','-i',str(video)],capture_output=True,text=True)
    duration=re.search(r'Duration: (\d+):(\d+):([\d.]+)',probe.stderr)
    seconds=int(duration[1])*3600+int(duration[2])*60+float(duration[3])
    decoded=subprocess.run([ffmpeg,'-hide_banner','-i',str(video),'-f','null','-'],capture_output=True,text=True,check=True)
    (out/'logs'/(video.stem+'-decode.txt')).write_text(decoded.stderr)
    raw=subprocess.check_output([ffmpeg,'-v','error','-i',str(video),'-vf','fps=4,scale=600:270','-f','rawvideo','-pix_fmt','rgb24','pipe:1'])
    size=600*270*3;total=len(raw)//size
    for offset in range(0,total,16):
        sheet=Image.new('RGB',(2400,1200),'#eee8df');draw=ImageDraw.Draw(sheet)
        for j,i in enumerate(range(offset,min(offset+16,total))):
            frame=Image.frombytes('RGB',(600,270),raw[i*size:(i+1)*size]);x=j%4*600;y=j//4*300
            sheet.paste(frame,(x,y));draw.text((x+8,y+275),f'{video.name}: {i/4:.2f}s',fill='black')
        sheet.save(review/(video.stem+f'-review-{offset//16+1:02}.jpg'))
    actions=json.loads(video.with_name(video.stem+'-actions.json').read_text())
    assert actions[-1]['end'] < seconds + 3, (video.name, actions[-1], seconds)
    # Host monotonic timestamps and encoder first-frame PTS have different origins.
    # Final-state visibility is checked on the continuous frame sheets.
    metadata[video.name]={'seconds':seconds,'samples4fps':total,'lastActionEnd':actions[-1]['end'],'wholeVideoDecoded':True}
    print(video.name,metadata[video.name],flush=True)
(out/'录屏元数据.json').write_text(json.dumps(metadata,ensure_ascii=False,indent=2))
files=sorted(p for p in out.glob('*.png') if 'overlay' not in p.name)
for offset in range(0,len(files),4):
    sheet=Image.new('RGB',(1600,820),'#eee8df');draw=ImageDraw.Draw(sheet)
    for j,p in enumerate(files[offset:offset+4]):
        im=Image.open(p).convert('RGB');im.thumbnail((800,360));x=j%2*800;y=j//2*410;sheet.paste(im,(x,y));draw.text((x+8,y+365),p.name,fill='black')
    sheet.save(review/f'screenshots-{offset//4+1:02}.jpg')
# Auxiliary reference overlay only. Original Android screen is kept untouched.
if (out/'17-standard-16x9-v1.png').exists():
    screen=Image.open(out/'17-standard-16x9-v1.png').convert('RGBA')
    ref=Image.open(out.parents[2]/'Rhine_reference.png').convert('RGBA').crop((7,12,1370,779)).resize(screen.size)
    Image.blend(screen,ref,.5).save(out/'26-reference-overlay-v1.png')
    (out/'参考叠图说明.txt').write_text('参考有效区(7,12,1370,779)对齐至16:9原图17：-> '+str(screen.size)+'；50%混合；辅助构图检查，原始截图17未修改。')
