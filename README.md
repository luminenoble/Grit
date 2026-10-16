# Grhinet

基于 [Grit](https://github.com/shub39/Grit) 修改的 Android 工作记录应用，保留原项目的 GPL-3.0 许可。

正式版：[Grhinet 6.0.8](https://github.com/luminenoble/Grit/releases/tag/6.0.8) · [下载 APK](https://github.com/luminenoble/Grit/releases/download/6.0.8/Grhinet-6.0.8-foss.apk)。选中类别使用浅色底、黑色描边，已完成任务使用深色描边。

数据以 Category → Task → Section 组织：类别和任务有独立描述及文字颜色，Section 支持工作内容、完成日期和手动排序。保留任务手动完成、今天视图、提醒、任务小组件和 JSON 备份。

本次架构调整移除 Habit、统计栏目和 AI 分解，统一米色基础主题。导航为 Task / Setting / Rhine。Rhine 已实现横屏沉浸式两层场景、有限浏览、尾插/删除、调整排序、搜索定位及独立显示设置。

新版使用独立的 grit_rhine_database，不读取、不迁移、不清空旧数据库。导入仅支持 grit_rhine、formatVersion=1 的新格式；旧数据由用户提供导出 JSON 后在应用外转换。导入前先校验并展示数量，确认后完整替换当前新库数据。

- [修改方案](docs/修改文档.md)
- [架构调整工作记录](docs/架构调整工作记录.md)
- [阶段07最终交付与限制](docs/创建Rhine/交付/阶段07/实现记录.md) · [APK](docs/创建Rhine/交付/阶段07/Grit_Rhine-stage07-v1.apk) · [性能报告](docs/创建Rhine/交付/阶段07/性能报告.md)

构建：`./gradlew :androidApp:assembleFossDebug`。
测试：`./gradlew :androidApp:testFossDebugUnitTest`；数据库测试需 Android 设备：`./gradlew :androidApp:connectedFossDebugAndroidTest`。
