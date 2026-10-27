from 采集工具 import *
import sys
label=sys.argv[1]
start(u=50,v=49,dataset='large',detail=False)
mutate_display({},save=True)
def swipe_forward():adb('shell','input','swipe',600,430,1800,783,400)
def swipe_back():adb('shell','input','swipe',1800,783,600,430,400)
record(f'{label}-large-browse-v1.mp4',[swipe_forward,swipe_back]*4)
