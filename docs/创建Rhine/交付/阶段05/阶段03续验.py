"""Assertion-based UI regression against debug fixtures; never opens or replaces user data."""
import importlib.util
from pathlib import Path
import time

spec=importlib.util.spec_from_file_location('capture',Path(__file__).with_name('采集工具.py'))
c=importlib.util.module_from_spec(spec);spec.loader.exec_module(c)
c.shot=lambda name: None
checks=[]
def done(name): checks.append(name);print('PASS '+name,flush=True)
def absent(text): assert not any(text in n.get('text','') for n in c.tree().iter('node')),text

def mutate(name):
    c.adb('shell','am','start','-f','0x20000000','-n',c.COMPONENT,'--es','mutation',name)
    time.sleep(1)

c.start();c.enter();mutate('move');c.expect('SECTION LAYER');absent('载入中…')
c.click(desc='返回类别层');c.expect('CATEGORY 03');c.expect('03   设计评审');done('moved Task leaves sibling directory; parent return follows new Category')
c.start();c.enter();c.click(desc='沿列下一格');mutate('reorder-section');c.expect('04   需求与范围');done('live Section reorder keeps stable identity and follows its new index')
c.start();c.enter();c.click(desc='沿列下一格');c.click(desc='返回类别层');mutate('reorder-section');c.enter();c.expect('04   需求与范围');done('cached Section position restores by ID after external reorder')
c.start();c.enter();mutate('delete-task');c.expect('SECTION LAYER');c.expect('空任务')
c.click(desc='返回类别层');c.expect('CATEGORY LAYER');c.expect('空任务');done('deleted Task falls back to real neighbour in surviving Category')
c.start();c.enter();c.click(desc='沿列下一格');mutate('delete-section');c.expect('01   草图评审');done('deleted focused Section resolves to the surviving item without stale ID')
c.start();c.enter();mutate('delete-category');c.expect('CATEGORY LAYER');c.expect('CATEGORY 02');done('deleted parent Category exits second layer to surviving neighbour')

(Path(__file__).parent/'阶段03续验断言结果.txt').write_text('\n'.join('PASS '+name for name in checks)+'\n')
print(f'{len(checks)} UI checks passed',flush=True)
