from pathlib import Path
import subprocess,json
from PIL import Image,ImageDraw
import imageio_ffmpeg
out=Path(__file__).resolve().parent
ffmpeg=imageio_ffmpeg.get_ffmpeg_exe()
metadata={}
for video in sorted(out.glob('*-v1.mp4')):
    reader=imageio_ffmpeg.read_frames(str(video));info=next(reader);reader.close()
    count,seconds=imageio_ffmpeg.count_frames_and_secs(str(video))
    raw=subprocess.check_output([ffmpeg,'-v','error','-i',str(video),'-vf','fps=4,scale=600:270','-f','rawvideo','-pix_fmt','rgb24','pipe:1'])
    size=600*270*3;total=len(raw)//size
    for offset in range(0,total,16):
        sheet=Image.new('RGB',(2400,1200),'#eee8df');draw=ImageDraw.Draw(sheet)
        for j,i in enumerate(range(offset,min(offset+16,total))):
            frame=Image.frombytes('RGB',(600,270),raw[i*size:(i+1)*size]);x=j%4*600;y=j//4*300
            sheet.paste(frame,(x,y));draw.text((x+8,y+275),f'{i/4:.2f}s',fill='black')
        sheet.save(out/(video.stem+f'-review-{offset//16+1:02}.jpg'))
    metadata[video.name]={'stream':info,'frames':count,'seconds':seconds,'review_samples':total}
    print(video.name,count,seconds,flush=True)
(out/'录屏元数据.json').write_text(json.dumps(metadata,ensure_ascii=False,indent=2))
