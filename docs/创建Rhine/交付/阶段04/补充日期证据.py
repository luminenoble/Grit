import 采集工具 as c
import time
c.start(3,1);c.click(desc='进入 Section 层');c.click(desc='沿列下一格');c.click(desc='尾部新增 SECTION')
c.scroll(1200,780,330);c.expect('完成日期');c.shot('24-new-section-date-v1.png')
fields=c.nodes(klass='android.widget.EditText');c.replace_field(len(fields)-1,'2099-01-01');c.back()
c.click(label='保存');c.expect('不能晚于今天');c.expect('新建 Section')
fields=c.nodes(klass='android.widget.EditText');c.replace_field(len(fields)-1,'2026-01-03');c.scroll(1200,240,290);c.shot('25-new-section-date-keyboard-v1.png')
c.click(label='保存');time.sleep(1);c.expect('DATE 2026-01-03');c.expect('01   unname');c.shot('26-new-section-past-date-saved-v1.png')
print('New Section rejects future date and saves past date with keyboard-visible actions',flush=True)
