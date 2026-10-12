"""12fps contact sheets around submit/delete completion, alongside full 4fps review."""
from pathlib import Path
import json,subprocess
from PIL import Image,ImageDraw
import imageio_ffmpeg
out=Path(__file__).resolve().parent
for prefix,action in [('22',3),('23',1)]:
    video=next(out.glob(prefix+'-*.mp4'))
    events=json.loads(video.with_name(video.stem+'-actions.json').read_text())
    start=max(0,events[action]['end']-2)
    raw=subprocess.check_output([imageio_ffmpeg.get_ffmpeg_exe(),'-v','error','-ss',str(start),'-i',str(video),'-t','4','-vf','fps=12,scale=600:270','-f','rawvideo','-pix_fmt','rgb24','pipe:1'])
    frame_bytes=600*270*3
    frames=[Image.frombytes('RGB',(600,270),raw[i:i+frame_bytes]) for i in range(0,len(raw),frame_bytes)]
    for page in range(0,len(frames),16):
        selected=frames[page:page+16]
        sheet=Image.new('RGB',(2400,300*((len(selected)+3)//4)),'#eee8df');draw=ImageDraw.Draw(sheet)
        for i,im in enumerate(selected):
            x=i%4*600;y=i//4*300;sheet.paste(im,(x,y));draw.text((x+8,y+275),f'{start+(page+i)/12:.3f}s',fill='black')
        sheet.save(out/(video.stem+f'-motion-review-{page//16+1}.jpg'))
    print(video.name,'12fps',start,'to',start+4,flush=True)
