"""Decode original screenrecord MP4s into 4 fps review sheets (requires Pillow, imageio-ffmpeg)."""
from pathlib import Path
import subprocess
import json
from PIL import Image, ImageDraw
import imageio_ffmpeg

out = Path(__file__).resolve().parent
ffmpeg = imageio_ffmpeg.get_ffmpeg_exe()
metadata = {}
for video in sorted(out.glob('*.mp4')):
    reader = imageio_ffmpeg.read_frames(str(video))
    info = next(reader)
    reader.close()
    count, seconds = imageio_ffmpeg.count_frames_and_secs(str(video))
    raw = subprocess.check_output([ffmpeg, '-v', 'error', '-i', str(video), '-vf', 'fps=4,scale=600:270', '-f', 'rawvideo', '-pix_fmt', 'rgb24', 'pipe:1'])
    frame_bytes = 600 * 270 * 3
    frames = [Image.frombytes('RGB', (600, 270), raw[i:i+frame_bytes]) for i in range(0, len(raw), frame_bytes)]
    sheet = Image.new('RGB', (2400, 300 * ((len(frames) + 3) // 4)), '#eee8df')
    draw = ImageDraw.Draw(sheet)
    for i, frame in enumerate(frames):
        x, y = i % 4 * 600, i // 4 * 300
        sheet.paste(frame, (x, y))
        draw.text((x + 8, y + 275), f'{i / 4:.2f}s', fill='black')
    sheet.save(out / (video.stem + '-review.jpg'))
    for page, top in enumerate(range(0, sheet.height, 1200)):
        sheet.crop((0, top, sheet.width, min(top + 1200, sheet.height))).save(out / (video.stem + f'-review-{page+1}.jpg'))
    metadata[video.name] = {'stream': info, 'encoded_frames': count, 'duration_seconds': seconds, 'sampled_frames_4fps': len(frames)}
    print(video.name, count, seconds, flush=True)
(out / '录屏元数据.json').write_text(json.dumps(metadata, ensure_ascii=False, indent=2))
