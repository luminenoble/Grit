# Grit 视觉优化 — 改动清单（阶段 1–4）

参考 HuskarUI（Ant Design）设计语言，在 Jetpack Compose 内实现。所有改动位于 `shared/ui` 的
`commonMain`，三平台（Android / jvm / wasmJs）通用。

## 新增文件

### `shared/ui/.../theme/GritTokens.kt`（阶段 1 · 设计 token 层）
集中式设计 token，收敛散落各处的魔法数字，仿 Ant Design「基准 + 派生 + 语义命名」思路：

- `GritRadius`：圆角阶（xs=4 / sm=8 / md=12 / lg=16 / xl=20 / xxl=28），外加 `join=6`
  用于连接式列表相邻卡片的过渡圆角。
- `GritSpacing`：间距阶（xs=4 / sm=8 / md=12 / lg=16 / xl=24）。
- `GritSemanticColors` + `gritSemanticColors()`：固定状态色（success / warning / error / info），
  取自 Ant Design 色板，并区分明暗两套；叠加在 MaterialKolor 动态主色之上，两套体系互不冲突。

## 修改文件

### `task/ui/component/TaskCard.kt`（阶段 2 + 3）
- 默认圆角 `RoundedCornerShape(4.dp)` → `GritRadius.md`（12dp）。
- 新增 1dp 细描边（`BorderStroke`）营造 Ant 卡片的层次感；**逾期任务**边框变红。
- 逾期判定：`!status && reminder != null && reminder < LocalDateTime.now()`。
- 逾期时提醒行（闹钟图标 + 时间）用 `semantic.error` 着色并加粗，绑定语义色到业务状态。
- 内边距 / 间距改用 `GritSpacing` token。

### `habit/ui/component/HabitCard.kt`（阶段 2 + 3）
- 新增 1dp 细描边；完成时边框转为主色高亮。
- 连续打卡的「火苗」图标在 streak > 0 时用 `semantic.warning`（橙）着色。

### `task/ui/section/TaskList.kt`（阶段 2 + 4）
- 连接式列表的所有圆角字面量（20dp / 4dp）改为 `GritRadius.xl` / `GritRadius.join`，
  相邻卡片过渡圆角由 4dp 微调为 6dp，观感更柔和统一。
- 主任务列表（可拖拽）与平板展开视图的任务项加入 `Modifier.animateItem()`，
  增删 / 重排时带 spatial 过渡动画。

## 验证与后续

- 未改动任何数据层 / ViewModel，纯 UI；保留了 AMOLED、Material You、动态取色分支。
- 沙盒仅有 JDK 11，无法运行项目的 ktfmt 0.61（需 JDK 17）。**提交前请在本地运行**
  `./gradlew spotlessApply` 以过 CI 格式化，再 `./gradlew :androidApp:assembleDebug` 编译验证。
- 代码风格已尽量对齐仓库现有 ktfmt kotlinlangStyle。
