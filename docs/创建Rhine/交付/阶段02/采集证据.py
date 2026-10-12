"""Run from repo root after installing stage02 APK. Only debug in-memory fixtures are used."""
from pathlib import Path
import subprocess
import time

ADB = '/mnt/d/professionalTools/AndroidStorage/sdk/platform-tools/adb.exe'
OUT = Path(__file__).resolve().parent
COMPONENT = 'com.shub39.grit.debug/com.shub39.grit.rhine.RhinePreviewActivity'

def adb(*args, binary=False):
    return subprocess.check_output([ADB, *map(str, args)], text=not binary)

def start(dataset, u, v, trace=False):
    adb('shell', 'am', 'start', '-S', '-n', COMPONENT, '--es', 'dataset', dataset, '--ef', 'u', u, '--ef', 'v', v, '--ez', 'traceTouch', str(trace).lower())
    expected = {'normal': '05 CATEGORIES', 'uneven': '03 CATEGORIES', 'empty': '00 CATEGORIES', 'one-empty': '01 CATEGORIES'}[dataset]
    time.sleep(3)
    for attempt in range(8):
        dumped = adb('shell', 'uiautomator', 'dump', '/sdcard/rhine02-ready.xml')
        if 'dumped to' not in dumped:
            time.sleep(1)
            continue
        tree = adb('exec-out', 'cat', '/sdcard/rhine02-ready.xml')
        if expected in tree and 'GRIT RHINE' in tree:
            time.sleep(1)
            return
        time.sleep(1)
    raise RuntimeError('Preview did not load: ' + dataset)

def shot(name):
    (OUT / name).write_bytes(adb('exec-out', 'screencap', '-p', binary=True))
    print(name, flush=True)

def swipe(x1, y1, x2, y2, duration):
    adb('shell', 'input', 'swipe', x1, y1, x2, y2, duration)

def record(name, dataset, u, v, actions):
    start(dataset, u, v, trace=True)
    adb('shell', 'dumpsys', 'gfxinfo', 'com.shub39.grit.debug', 'reset')
    original_touches = adb('shell', 'settings', 'get', 'system', 'show_touches').strip()
    adb('shell', 'settings', 'put', 'system', 'show_touches', '1')
    remote = '/sdcard/' + name
    proc = subprocess.Popen([ADB, 'shell', 'screenrecord', '--time-limit', '12', '--bit-rate', '10000000', remote], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    try:
        time.sleep(1)
        for action in actions:
            action()
            time.sleep(1.3)
        proc.communicate(timeout=20)
    finally:
        if original_touches == 'null':
            adb('shell', 'settings', 'delete', 'system', 'show_touches')
        else:
            adb('shell', 'settings', 'put', 'system', 'show_touches', original_touches)
    if proc.returncode:
        raise RuntimeError('screenrecord failed')
    (OUT / name).write_bytes(adb('exec-out', 'cat', remote, binary=True))
    (OUT / (name.removesuffix('.mp4') + '-gfx.txt')).write_text(adb('shell', 'dumpsys', 'gfxinfo', 'com.shub39.grit.debug'))
    shot(name.removesuffix('.mp4') + '-end.png')
    print(name, flush=True)

if __name__ == '__main__':
    for name, dataset, u, v in [
        ('01-start-v1.png', 'normal', 0, 1),
        ('02-middle-v1.png', 'normal', 8, 1),
        ('03-tail-v1.png', 'normal', 19, 1),
        ('04-uneven-u99-v1.png', 'uneven', 99, 0),
        ('05-short-column-u99-v1.png', 'uneven', 99, 1),
        ('06-empty-v1.png', 'empty', 0, 0),
        ('07-one-empty-category-v1.png', 'one-empty', 0, 0),
        ('08-uneven-tail-u101-v1.png', 'uneven', 101, 0),
    ]:
        start(dataset, u, v)
        shot(name)
    record('09-u-peak-handoff-v1.mp4', 'normal', 2, 1, [
        lambda: swipe(650, 420, 1250, 596, 900),
        lambda: swipe(1300, 650, 950, 547, 650),
        lambda: adb('shell', 'input', 'tap', 2300, 137),
    ])
    record('10-v-switch-snap-v1.mp4', 'normal', 8, 1, [
        lambda: swipe(1800, 300, 1280, 588, 700),
        lambda: swipe(1250, 680, 1770, 392, 650),
        lambda: adb('shell', 'input', 'tap', 1682, 940),
    ])
    record('11-tail-fling-blocked-v1.mp4', 'uneven', 98, 0, [
        lambda: swipe(650, 420, 1500, 670, 90),
        lambda: swipe(650, 420, 1500, 670, 150),
        lambda: swipe(650, 420, 1500, 670, 700),
    ])
    # Change only the emulator's viewport; restore it even if capture fails.
    start('uneven', 99, 0)
    try:
        adb('shell', 'wm', 'size', '1080x2000')
        time.sleep(2)
        shot('12-resized-focus-u99-v1.png')
    finally:
        adb('shell', 'wm', 'size', 'reset')
    time.sleep(2)
    shot('13-restored-focus-u99-v1.png')
