# Grhinet

基于 [Grit](https://github.com/shub39/Grit) 修改的 Android 工作记录应用，保留原项目的 GPL-3.0 许可。

正式版：[Grhinet 6.2.4](https://github.com/luminenoble/Grit/releases/tag/6.2.4) · [下载 APK](https://github.com/luminenoble/Grit/releases/download/6.2.4/Grhinet-6.2.4-foss.apk)。Task、设置、对话框和小组件采用暖色液态玻璃，小组件标题支持渐变色；Rhine 的块是光盘般的薄片磨砂玻璃，外形清楚、只有隔着玻璃的后方模糊，每列整体染上列首的颜色（颜色浓度、模糊、白度、颗粒可在显示设置中调），标题与插图同在块的长侧面，并使用底部玻璃工具栏、抬升转向动画、分屏编辑器，支持为类别和任务添加本机图片。现行设计见 [docs](docs/README.md)。

数据以 Category → Task → Section 组织：类别和任务有独立描述，类别、任务和 Section 各有文字颜色（纯色或至多 5 色渐变，可输入 RGB、复制粘贴），Section 支持工作内容、完成日期和手动排序。保留任务手动完成、今天视图、提醒、任务小组件和 JSON 备份。

相对上游移除了 Habit、统计栏目和 AI 分解，统一米色基础主题。导航为 Task / Setting / Rhine。Rhine 已实现横屏沉浸式两层场景、有限浏览、尾插/删除、调整排序、搜索定位及独立显示设置。

新版使用独立的 grit_rhine_database，不读取、不迁移、不清空旧数据库。导入仅支持 grit_rhine、formatVersion=1 的新格式；旧数据由用户提供导出 JSON 后在应用外转换。导入前先校验并展示数量，确认后完整替换当前新库数据。

文档：[项目概览](docs/项目概览.md) · [Rhine](docs/Rhine.md) · [开发与发布](docs/开发与发布.md)。历史方案与阶段记录在 [docs/归档](docs/归档/README.md)。

构建：`./gradlew :androidApp:assembleFossDebug`。
测试：`./gradlew :androidApp:testFossDebugUnitTest`；数据库测试需 Android 设备：`./gradlew :androidApp:connectedFossDebugAndroidTest`。
