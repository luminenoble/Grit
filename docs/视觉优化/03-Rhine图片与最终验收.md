# 阶段 3：Rhine 图片与最终验收

前置：[总览](README.md)决策 D6（独立表）、D7（入口位置）、D9（可见高度），以及[阶段 2](02-Rhine界面与动效重构.md)的位姿、抬升和编辑分屏。

## 1. 可行性结论

**可行，不需要新的媒体存储架构。**

- 用系统照片选择器（`ActivityResultContracts.PickVisualMedia`，仅图片），不申请任何存储/媒体权限。Android 13+ 与经 Google Play 系统更新回移的设备使用照片选择器，其余设备由 AndroidX 自动回退到 `ACTION_OPEN_DOCUMENT`。两者返回的 `content://` URI 都支持 `takePersistableUriPermission(FLAG_GRANT_READ_URI_PERMISSION)`，授权在重启后仍然有效。
- 不复制原图，不建应用图片目录：只保存 URI，按需解码为限定尺寸的内存纹理。
- 绘制沿用现有贴面文字的面局部坐标变换（`FaceFrame` + 矩阵），图片自然贴在侧面上，并随阶段 2 的位姿旋转。

**限制（如实说明）**：

1. 用户在相册中删除照片，或在系统设置中撤销授权后，图片不可读。此时显示“图片不可用”，可重新选择或移除，不会崩溃。只存引用就无法避免这种情况。
2. 持久授权有上限（Android 11+ 为每应用 512 条，更早版本为 128 条）。同一 URI 只占一条授权，未引用的授权会主动释放，个人使用不会触及上限；如果真的达到上限，选图时提示，不保存。
3. URI 只在本机有效，不能随 JSON 备份迁移到其他设备（D6）。

如果用户更看重“照片删除后插图仍在”，最小的安全替代方案是：保存时额外存一张长边 1024px 的 JPEG 缩略图到应用私有目录。这属于提示词禁止“擅自引入”的新存储方式，**本设计不实施**，只在用户明确要求时作为后续选项。

## 2. 选图架构

### 2.1 入口（D7：Rhine 编辑器与 Task 页编辑器都提供）

插图行 `IllustrationField`（缩略图 + “选择图片 / 更换 / 移除”，不可读时显示“图片不可用”）放进共享表单组件 `CategoryUpsertSheet`、`TaskUpsertSheet` 内部，位于名称字段上方。新增可选参数 `illustration: IllustrationDraft? = null`，为 null 时不显示，所以 `SectionEditor` 等其他调用不受影响。

| 入口 | 位置 | 保存路径 |
| --- | --- | --- |
| Rhine：编辑/新建 Category、Task | 分屏编辑器右侧面板（阶段 2 第 7 节），左侧预览实时显示 | `RhineScreen.submit()`：对象保存得到 id 后写插图 |
| Task 页：新建 Task（`TaskRoutes.AddTask`，`TasksPage.kt:131`） | 全屏表单 | `TaskAction.UpsertTask` 增加可选 `illustration: IllustrationChange?`，`TasksViewModel` 在 `upsertTask` 返回 id 后写插图 |
| Task 页：编辑 Task（`TasksPage.kt:207`） | 同上 | 同上 |
| Task 页：新建/编辑 Category（`TaskRoutes.CategoryEdit`，`TasksPage.kt:181`） | 同上 | `TaskAction.AddCategory` 增加同样的可选字段 |

`IllustrationChange` 为 `Keep / Set(uri) / Remove`，默认 `Keep`，现有调用（列表内勾选 Section、切换完成等）不传，行为不变。Task 页只编辑插图，不显示插图：任务卡、类别按钮和小组件外观不变，插图只在 Rhine 中绘制（第 5 节）。

### 2.2 流程

```text
插图行（Rhine 编辑器 / Task 页表单）
   │ 点“选择图片”
   ▼
rememberRhineImagePicker()            commonMain expect
   ├─ androidMain：rememberLauncherForActivityResult(PickVisualMedia(ImageOnly))
   │      → takePersistableUriPermission(uri, READ)   （失败：提示，不进入草稿）
   │      → 返回 uri.toString()
   └─ jvm / wasm：不可用（rhineImagePickerAvailable() = false，按钮隐藏）
   ▼
草稿 illustration = uri（只在编辑器内存中，未落库）
   ▼  保存
Rhine：submit()；Task 页：TasksViewModel
   upsertCategory / upsertTask → 得到 id → illustrationRepo.setIllustration(owner, id, uri)
```

- 权限清单不变：不新增 `READ_MEDIA_IMAGES`（清单中已移除 `READ_EXTERNAL_STORAGE`）。
- `androidx.activity:activity-compose` 已在依赖树中（MainActivity 使用 `enableEdgeToEdge`，FileKit 也依赖它）。第二期在 `shared/ui` 的 androidMain 中显式声明，不引入新库。
- 草稿取消：草稿中新取得的授权，如果数据库中没有其他引用，就立即释放。
- 保存顺序：对象保存成功后再写插图。插图写入失败不回滚对象（对象数据优先），提示“已保存，插图未保存，请重试”。Rhine 编辑器按“已提交但定位失败”路径退出（阶段 2 第 8.2 节）；Task 页按现有 `onSaved` 关闭表单并显示同一提示。

## 3. 数据模型（D6）

不修改 `Category` / `Task` 模型、`RhineBackup` 格式 v1 或小组件；Task 页只增加 2.1 所述的可选保存字段。新增独立表：

```kotlin
@Entity(
    tableName = "rhine_illustrations",
    foreignKeys = [
        ForeignKey(CategoryEntity::class, ["id"], ["categoryId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(TaskEntity::class, ["id"], ["taskId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("categoryId", unique = true), Index("taskId", unique = true), Index("uri")],
)
data class IllustrationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryId: Long?,      // 二者恰有一个非空，由仓库校验
    val taskId: Long?,
    val uri: String,
    val updatedAt: Instant,
)
```

`shared/core` 新接口（不扩展 `TaskRepo`，避免改动它的所有实现与测试替身）：

```kotlin
enum class IllustrationOwner { Category, Task }

interface RhineIllustrationRepo {
    /** 只查询可见列的 ID，不全量读取。 */
    fun illustrationsFlow(owner: IllustrationOwner, ids: Set<Long>): Flow<Map<Long, String>>
    suspend fun illustration(owner: IllustrationOwner, id: Long): String?
    suspend fun setIllustration(owner: IllustrationOwner, id: Long, uri: String?) // null = 移除
    suspend fun referencedUris(): Set<String>
}
```

实现：`androidApp` 中 `IllustrationDao` + `RhineIllustrationRepository`（Koin 注册）；`webDemo` 与 `RhinePreviewActivity` 夹具提供内存实现。

## 4. URI 生命周期

| 事件 | 处理 |
| --- | --- |
| 选图 | 立即取持久授权；草稿保存 URI |
| 编辑器取消 / 换图 / 移除 | 若该 URI 在表中无其他引用，`releasePersistableUriPermission` |
| 删除 Category / Task | 外键级联删除行；授权在对账时释放 |
| 导入备份（整库替换） | 旧对象全部删除，级联清空插图表；对账释放授权 |
| 应用启动（后台协程） | 对账：`contentResolver.persistedUriPermissions` 减去 `referencedUris()`，逐个释放 |
| 读取失败（删除 / 撤销 / 不存在） | 标记为 Missing，保留行；概览中按“无图”绘制，编辑器显示“图片不可用 · 重新选择 / 移除” |

## 5. 可见规则

### 5.1 索引与位置（已核对）

- 每列 u=0 的槽位是 `RhineBlockKind.Zero`（`RhineScene.kt:92–103`），代表父对象本身：第一层为 Category，第二层为该列的 Task。
- 投影中 `eU.x = −0.959 < 0`：u 增大时向左，因此 u=0 是每列最右（最右下）的块，即提示词所说的“每列最右侧、Category 0 号”的块。

### 5.2 规则（纯函数，单元测试）

```kotlin
sealed interface IllustrationContext {
    data object Overview : IllustrationContext
    data class Mutation(val target: RhineSlotKey) : IllustrationContext   // 新增落位 / 删除溶解
    data class Editor(val selected: RhineSlotKey) : IllustrationContext
}

fun showsIllustration(slot: RhineSlot, ctx: IllustrationContext): Boolean = when (ctx) {
    Overview    -> slot.kind == Zero && slot.column != null
    is Mutation -> (slot.kind == Zero && slot.column != null) || slot.key == ctx.target
    is Editor   -> slot.key == ctx.selected
}
```

| 场景 | 第一层 | 第二层 |
| --- | --- | --- |
| 概览 | 各列 0 号块显示 **Category** 插图；Task 块不显示 | 各列 0 号块显示该列 **Task** 插图；Section 块不显示 |
| 新增（退出动画中下落到新槽位）、删除（溶解） | 目标块额外显示自身插图 | 同左 |
| 编辑分屏 | 只有选中块（上下文透明） | 同左 |
| 返回概览后 | 恢复概览规则（状态机回到 `Idle` 时上下文复位） | 同左 |

> 2026-10-21 补充（D12）：第一层的 Task 块另外以插图条显示自己的插图，见 [04 文档](04-任务插图条.md)。上表的大图规则不变。

### 5.3 实际可见高度（D9）

图片画在侧面文字下方，要求图片区高 ≥ 18dp、宽 ≥ 48dp，否则不画（不出现残缺的小条）。默认几何下，非中心列的块高 16dp，所以概览中**实际只有中心列的 0 号块能显示插图**：焦点在列首附近时约 110dp 高，焦点移到 u≈10 时约 46dp，再远则不显示。0 号块长侧面会被列首之前（u < 0）抬升的装饰块挡住；有插图时侧面按完整高度绘制，与之重叠的装饰块以 28% 不透明度绘制（D11），块的排列不变。在编辑分屏和新增/删除动画中，块按阶段 2 放大或单独展示，插图总是可见。

## 6. 贴面绘制

只改 `front` 面（`u0` 处的长侧面，朝右下，原本显示列标签）。面局部坐标：x 沿 V，`0..L`；y 向下，`0..frontVisible`，`(0,0)` 为该面左上角。

```text
x:0                                                          L
y:0 ┌──────────────────────────────────────────────────────────┐
    │ pad      02  工作（原标签，有图时顶对齐，无图时居中）        │ ← 文字带 tb = 行高 + pad/2
    │ ┌──────────────────────────────────────────────────────┐ │ ← tb + 3dp
    │ │                                                      │ │
    │ │                插图（裁剪区，圆角 2dp）                 │ │
    │ │                                                      │ │
    │ └──────────────────────────────────────────────────────┘ │ ← frontVisible − 3dp
    └──────────────────────────────────────────────────────────┘
       ↑ pad = 8dp（与文字内边距相同）
```

- 在 `drawRhineScene` 的侧面标签之后、同一 `withTransform(faceMatrix)` 内绘制：`clipPath(圆角矩形)` → `drawImage(bitmap, srcRect, dstRect)` → 叠加一层该面的明暗渐变（`frontAt` → `frontLowAt`，透明度 0.18），使插图与块的光照一致 → 0.5dp `style.edge` 描边，嵌入感。
- 文字内容、颜色、字号、滚动规则都不变；有图时只把文字从垂直居中改为顶部对齐。
- 插图不超出面边界，不覆盖顶面、端面或相邻块（画家序与面裁剪保证）。
- 新图到达时 150ms 淡入，减少动效时直接出现。

## 7. 裁剪与缩放设置

`RhineDisplayConfig` 新增字段（本机显示偏好，不进入备份）：

```kotlin
enum class RhineImageFit { CropTopStart, CropCenter, Fit }
val illustrationFit: RhineImageFit = RhineImageFit.CropTopStart
```

源矩形计算（`dst = w×h`，图片 `iw×ih`），纯函数并做单元测试：

| 模式 | 计算 | 效果 |
| --- | --- | --- |
| `CropTopStart`（默认） | `s = max(w/iw, h/ih)`；`src = (0, 0, w/s, h/s)` | 保持比例，填满，从左上角裁剪，不拉伸 |
| `CropCenter` | 同上，`src` 居中 | 保持比例，填满，居中裁剪 |
| `Fit` | `s = min(w/iw, h/ih)`；`dst` 缩小并居中，`src` 为整图 | 完整显示，空余部分为面本身的颜色 |

设置入口：`RhineDisplayPage`“标记/效果”页签增加 `GlassSegmented`：“填充·左上 / 填充·居中 / 完整显示”，左侧预览用内置的示例插图实时展示（示例为几 KB 的 compose 资源，不是用户数据）。编辑分屏、概览、新增/删除动画统一使用这一设置。

## 8. 性能与纹理生命周期

| 要求 | 设计 |
| --- | --- |
| 不解码原图 | `ImageDecoder.setTargetSize`，长边分三档：384 / 768 / 1280px。按目标面像素 ×1.25 向上取档。默认 914×411dp、420dpi 时，概览约需 543px（取 768），编辑约 920px（取 1280） |
| 不一次加载全部 | 请求集合 = 可见窗口中有插图且满足 5.3 高度的 0 号块 + 编辑选中块，通常 ≤ 3 个。由 `snapshotFlow` 从可见窗口得出，防抖 100ms |
| 只查可见 ID | `illustrationsFlow(owner, ids)` 只用当前查询窗口的列 ID（沿用 `queryWindow()` 分块，不逐帧查询） |
| 异步 | `Dispatchers.IO.limitedParallelism(2)`；不再需要的请求立即取消 |
| 内存上限 | LRU 按字节，24MB（1280 档约 4.9MB/张），离开 Rhine 或 `onTrimMemory(UI_HIDDEN)` 时清空 |
| 释放 GPU 资源 | 被淘汰的位图不再被引用后由系统回收；不对仍可能在绘制的位图调用 `recycle()` |
| 缺失不崩溃 | 解码异常（`FileNotFoundException`、`SecurityException`、`IOException`、解码错误）全部捕获为 Missing，负缓存 30 秒；打开编辑器时重试 |
| 绘制开销 | 每帧最多绘制 ≤ 3 张已解码位图，只有矩阵变换和裁剪，无额外离屏 |

## 9. 迁移与兼容

- 数据库 `SCHEMA_VERSION` 1 → 2，`@AutoMigration(from = 1, to = 2)` 只新增表，原有三张表的数据不变。导出 `schemas/.../2.json`；用 `room3-testing` 的迁移测试验证。与任何架构升级一样，不支持安装旧版本降级。
- JSON 备份格式 v1 不变，旧备份照常导入。设置页备份说明中增加一句“插图仅保存在本机，不随备份导出”。
- 显示配置仍为版本 1：新字段缺省时取默认值（解码逻辑只清洗已存在的键）；未知枚举值由 `coerceInputValues` 回退到默认；旧版本应用读取新配置时由 `ignoreUnknownKeys` 忽略该字段。
- `webDemo`、JVM/Wasm：选图不可用，插图仓库为内存桩，Rhine 照常运行。

## 10. 最终验收（第三期执行）

### 10.1 功能

| 项目 | 方法 |
| --- | --- |
| 任务新建、编辑、删除、完成、排序、今天视图、提醒 | 现有设备测试 `TaskRepositoryTest` 等 + 手动流程 |
| 设置保存、恢复默认、降低透明度、插图显示模式在重启后保持 | `RhineDisplayPersistenceTest` 扩展 + 新增用例 |
| 小组件渲染、切换完成、切换范围、刷新、打开应用 | 桌面实操截图（阶段 07 记录为“未验证”，本期尽量补齐） |
| 两层导航（1.0s 进入、原样返回），历史与焦点恢复 | `RhineNavigationTest` + 录屏 |
| 预览 / 详情 / 调整模式；从右侧区迁出的每个操作都可达 | 逐项按第 2 阶段 1.1 表在工具栏上点击验证（宽屏与窄屏） |
| Category / Task 新建、编辑、删除、排序（含 U=99 尾插、删除最长列） | 现有 `RhineMutationRepositoryTest`、`RhineAdjustRepositoryTest` + `RhinePreviewActivity` 夹具 |
| 编辑器进出后相机与默认画面完全恢复 | 单元测试（终点位姿 == Identity）+ 前后截图逐像素比对 |
| 插图选择、重启后仍在、裁剪模式、可见规则、照片被删后的提示 | 设备手动 + `showsIllustration` 单元测试 |
| Task 页表单选图/更换/移除后，Rhine 中对应 0 号块同步；Task 页其他保存（勾选 Section、切换完成）不改变插图 | 设备手动 + `TasksViewModel` 单元测试（`IllustrationChange` 默认 `Keep`） |
| 返回键与中断：进入动画中返回、动画中旋转屏幕/改字号、快速连点、保存失败（`failSaveOnce`）、慢提交（`submitDelayMs`）、导入替换 | 夹具注入 + 录屏 |

### 10.2 视觉

材质一致；玻璃上文字对比度达标；宽屏与窄屏布局；编辑构图不裁切块体；三个过渡无跳帧和闪烁；插图位置、圆角和裁剪正确；Android 10–11 无模糊回退与 12+ 对照。

### 10.3 工程

- `:androidApp:assembleFossDebug`、`testFossDebugUnitTest`、`connectedFossDebugAndroidTest`，JVM / Wasm source set 与 `:webDemo` 编译。
- 性能：沿用阶段 07 的 `RhinePerformanceTest` 与 debug `RhineMetrics`，记录玻璃模糊开/关、过渡期间的帧耗时，并如实标注 Debug 模拟器的限制（阶段 07 记录过 Debug 未达 60Hz）。
- 内存：反复进出编辑器 20 次、浏览含插图的列，记录纹理缓存的字节数和 Java 堆。
- 回归：阶段 03–05 的交互清单重跑。

### 10.4 交付

- 截图与录屏都不提交入库（D10，用户 2026-10-17 决定）：保存在仓库外的工作目录（第三期开始时写明路径，并加入 `.gitignore` 防止误提交），交付记录只写文件名、环境与自检结论。
- 按里程碑提交到 `visual-optimize`，最后 `git push -u origin visual-optimize`，报告提交号与推送结果；不创建 issue 或 PR（AGENTS.md）。
