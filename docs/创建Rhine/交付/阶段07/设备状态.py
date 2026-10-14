from 采集工具 import *
import sys
p=OUT/'设备初始状态.json'
keys=[('system','font_scale'),('global','animator_duration_scale'),('global','transition_animation_scale'),('global','window_animation_scale'),('secure','show_ime_with_hard_keyboard'),('secure','stylus_handwriting_enabled')]
def snapshot():
    return {'size':adb('shell','wm','size').strip(),'density':adb('shell','wm','density').strip(),'settings':{f'{ns}/{key}':adb('shell','settings','get',ns,key).strip() for ns,key in keys},'overlays':adb('shell','cmd','overlay','list').strip(),'model':adb('shell','getprop','ro.product.model').strip(),'sdk':adb('shell','getprop','ro.build.version.sdk').strip(),'release':adb('shell','getprop','ro.build.version.release').strip()}
if len(sys.argv)>1 and sys.argv[1]=='restore':
    state=json.loads(p.read_text())
    for kind in ['size','density']:
        value=state[kind];override=re.search(r'Override \w+: ([\dx]+)',value)
        adb('shell','wm',kind,override.group(1) if override else 'reset')
    for name,value in state['settings'].items():
        ns,key=name.split('/')
        adb('shell','settings','delete',ns,key) if value=='null' else adb('shell','settings','put',ns,key,value)
    for line in state['overlays'].splitlines():
        if 'display.cutout.emulation' in line and line.startswith('['):
            enabled=line.startswith('[x]');overlay=line.split()[-1]
            adb('shell','cmd','overlay','enable' if enabled else 'disable',overlay)
    result=snapshot();(OUT/'设备恢复状态.json').write_text(json.dumps(result,ensure_ascii=False,indent=2))
    for name in ['size','density','settings','overlays']:assert result[name]==state[name],name
    print('Restored device size/density/font/animation/IME/cutout to initial state')
else:
    assert not p.exists(),'Keep original state'
    p.write_text(json.dumps(snapshot(),ensure_ascii=False,indent=2))
