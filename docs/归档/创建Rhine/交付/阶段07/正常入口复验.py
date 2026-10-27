"""Read-only normal navigation. Never creates/imports/edits business data or saves preferences."""
from 采集工具 import *
import struct
adb('shell','am','start','-S','-n','com.shub39.grit.debug/com.shub39.grit.app.MainActivity')
time.sleep(4)
print('Initial accessible labels',texts(),flush=True)
def click_nav(label):
    if nodes(label=label):click(label=label)
    else:click(desc=label)
if nodes(label='Rhine') or nodes(desc='Rhine'):
    click_nav('Rhine')
expect('GRIT RHINE');shot('35-normal-rhine-entry-v1.png')
click(label='TASK ↗');time.sleep(2)
raw=adb('exec-out','screencap','-p',binary=True);w,h=struct.unpack('>II',raw[16:24]);assert w<h,(w,h)
print('PASS normal MainActivity Rhine→Task restores portrait',flush=True)
click_nav('Rhine');expect('GRIT RHINE');click(label='SETTING ↗');time.sleep(2)
raw=adb('exec-out','screencap','-p',binary=True);w,h=struct.unpack('>II',raw[16:24]);assert w<h,(w,h)
print('PASS normal MainActivity Rhine→Setting restores portrait',flush=True)
expect('Rhine');print('PASS normal Task/Setting/Rhine entry graph',flush=True)
