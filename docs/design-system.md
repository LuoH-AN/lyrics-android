# FlClash 视觉设计体系

> 版本 0.9.1 · 覆盖 `lib/ui/`（90 个文件，约 29k 行）、`lib/views/`、`lib/widgets/`、`lib/features/`（约 43k 行）
>
> 本文描述 FlClash 的外观设计：色彩、形状、字体、图标、动效、材质、布局与各页面组件。
> 每一条数值都对应代码中的具名 token，并由 `test/lint/` 下的 18 个护栏测试强制。

---

## 目录

1. [设计哲学](#1-设计哲学)
2. [色彩系统](#2-色彩系统)
3. [表面层级](#3-表面层级)
4. [状态色](#4-状态色)
5. [形状系统](#5-形状系统)
6. [字体系统](#6-字体系统)
7. [图标系统](#7-图标系统)
8. [动效系统](#8-动效系统)
9. [Liquid Glass 材质](#9-liquid-glass-材质)
10. [布局与响应式](#10-布局与响应式)
11. [导航](#11-导航)
12. [应用栏](#12-应用栏)
13. [列表与设置行](#13-列表与设置行)
14. [面板：Sheet / Dialog / Popup](#14-面板-sheet--dialog--popup)
15. [表单与输入](#15-表单与输入)
16. [滚动](#16-滚动)
17. [仪表盘](#17-仪表盘)
18. [代理页](#18-代理页)
19. [配置页](#19-配置页)
20. [加载与空状态](#20-加载与空状态)
21. [可访问性与平台适配](#21-可访问性与平台适配)
22. [设计护栏](#22-设计护栏)
23. [附：token 速查表](#23-附token-速查表)

---

## 1. 设计哲学

FlClash 不是"Material 应用"，而是**用 Material 底座重建 Apple 交互语汇**。仓库自己的规则写得很明确：

> Material You from `material_ui` as the base, with Apple's interaction conventions (continuous corners, sheets that snap between detents, a floating tab bar) built on Material widgets rather than Cupertino ones.
> —— `.agents/skills/ui-work/SKILL.md`

三条支柱：

| 支柱 | 含义 |
|---|---|
| **用填充画结构，用颜色标状态** | 面与面之间靠明度差一级区分，永不靠边框。颜色只出现在"选中/开启/失败/唯一主操作"上 |
| **所有圆角都是超椭圆** | 连胶囊也不例外。`StadiumBorder`、`ClipRRect`、`BoxDecoration(borderRadius:)` 全部被 lint 禁止 |
| **动效以 iOS 27 实测为基准** | 弹簧参数、sheet 阻尼、玻璃光学、菜单托盘，全部是从 UIKit 模拟器逐帧测量后拟合的 |

设计令牌分两层：`lib/ui/` 是与应用无关的组件与令牌（形状、表面、动效、颜色、脚手架、面板、弹出、列表、字形）；`lib/widgets/` 是由它们构建、读取 provider 的应用组件。

---

## 2. 色彩系统

### 2.1 种子方案

`seededColorScheme()`（`lib/ui/color.dart`）基于 Material 的 HCT，但**替换三组角色**，其余保持 Material。

| 风格 | Material 变体 | primary | 表面 |
|---|---|---|---|
| `vivid` | content | 种子本身 | 近灰 |
| `tinted` | content | 种子本身 | 种子色相，chroma ≤ 10 |
| `soft` | tonal spot | Material 的，降饱和 | 近灰 |
| `monochrome` | monochrome | 黑或白 | 灰 |

Material 的九种变体大多只移动 secondary / tertiary——而应用几乎不显示这两者——所以表面保持灰调后它们看起来一样。四种风格各改变一件读者立刻能看到的事。

**关键数值**：

| 常量 | 值 | 作用 |
|---|---|---|
| `_surfaceChroma` | 3.0 | 所有 `surface*` 角色的 chroma 上限 |
| `_paperChroma` | 1.5 | 浅色 `surface` / `surfaceBright` 的上限 |
| `outlineVariant` chroma | 4 | 描边变体的上限 |
| `_tintDivisor` | 6.0 | `tinted` 风格取种子 chroma 的 1/6 |
| `_maxTintChroma` | 10.0 | `tinted` 的 chroma 上限 |
| `_primaryContainerChroma` | 48.0 | `primaryContainer` 的 chroma 上限 |
| `_minAccentTone` / `_maxAccentTone` | 35 / 60 | 浅色 primary 的 tone 钳位 |
| `_darkAccentLift` | 3.0 | 深色 primary 提升的 tone 数 |
| `_minDarkAccentTone` / `_maxDarkAccentTone` | 50 / 75 | 深色 primary 的 tone 钳位 |

`primaryContainer` 取 primary 色板 tone 90（浅）/ 30（深），`onPrimaryContainer` 反之。不加 chroma 上限时，黄色种子会填出纯黄，红色种子会读成错误色，深色模式则是浅块配浅字。

`vivid` 与 `tinted` 把种子本身当 `primary`——像 Apple 的强调色：主题里选的颜色就是每个勾选、开关和填充按钮的颜色。Material 对任何种子都设 tone 40/80，会把 iOS 蓝变成藏青。

### 2.2 默认种子色板

Apple 系统色，按 macOS 强调色顺序（`lib/common/constant.dart:165`）：

```
系统色 · #AF52DE 紫 · #FF2D55 粉 · #FF3B30 红
#FF9500 橙 · #FFCC00 黄 · #34C759 绿 · #8E8E93 灰
```

### 2.3 派生工具

```dart
Color.opacity80 / opacity60 / opacity50 / opacity38 / opacity30 / opacity15 / opacity12 / opacity10 / opacity0
Color.tone                       // HCT 明度
Color.shiftTone(double delta)    // 沿明度轴移动，钳在 0..100
Color.hex                        // #RRGGBB
```

`toPureBlack(bool)`：深色模式把所有表面角色整体下移 `surface.tone`，`surface` 落到纯黑。

---

## 3. 表面层级

`SurfaceLevel`（`lib/ui/surface.dart`）只有四级，且**只往下沉一级**：

| Level | 浅色 | 深色 | 承载 |
|---|---|---|---|
| `page` | `surfaceContainer` | `surface` | 页面底、表单 |
| `card` | `surfaceBright` | `surfaceContainer` | 卡片、成组行、浅色浮动层 |
| `block` | `surfaceContainer` | `surfaceContainerHigh` | 未选中分段、卡内瓷砖与井 |
| `inset` | `surfaceContainerHighest` | `surfaceContainerHighest` | 块内小标记：字形井、徽章、轨道 |

**`inset` 是地板**——需要第五级的设计说明它需要更少的容器。

浅色模式在灰与纸之间交替：灰页面上的近白卡片，卡片里的灰块。卡片是 tone 98 而非纯白，页面 tone 94：纯白挨着带色灰会刺眼，灰会显脏。深色模式只变亮，因为更暗的一级读起来是洞而不是层。

### 3.1 三条派生规则

- **组件永不自己选层级**。`CommonCard` 等共享表面读 `context.raisedSurfaceLevel` 并用 `SurfaceScope` 包裹子级——同一行在页面上是白、在卡片里是灰。按 `isInBottomSheet` 或摆放位置分支选填充，正是它要取代的 bug。
- **Sheet 是 `page` 级**，与页面同色，所以同一区块在 sheet 里和页面里长得一样。深色下 sheet 整体抬升一级（`ColorScheme.elevated`），因为深色页面上的遮罩几乎不压暗，同色 sheet 会读成"更多页面"而不是"页面上的一层"。纯黑下改用普通深色 + 抬升，以保留种子的色调。
- **浮动层取 `floatingOf`**：浅色 `card`、深色 `block`。深色页面上投影不可见，所以浮动层必须比它盖住的卡片更亮。

### 3.2 窗口 chrome

`ColorScheme.chromeSurface` = 浅色 `surfaceContainerHighest` / 深色 `surfaceContainerLow`，距页面 **4 个 tone**，并沿与内容相接的边画一条分隔线。在 `page` 级时侧边栏会与内容糊在一起；深色下 chrome 保持在 `card` 之下，卡片才仍然突出。

---

## 4. 状态色

**颜色标记唯一不同的那一项，永不标记它周围的结构。**

### 4.1 选中

| 场景 | 做法 |
|---|---|
| 成组行中的一项 | 保持本级填充，行缘画 `SelectionCheck`（24 dp 填充勾，primary） |
| 独立卡片 / 瓷砖 | `SelectionBadge`（22 dp 圆形底 + 填充字形），配 40 dp 图标井 |
| 多选编辑模式 | 圆形 checkbox，行保持本级填充 |
| 代理组使用中 | `SelectionBadge` 勾选徽章 |
| 代理组首选（mihomo `fixed`） | `SelectionBadge.preferred` 图钉：使用中实心，回落时 tonal |
| 分段控件 / 模式开关 | 实心 `primary` + `onPrimary` |

`CommonCard.isSelected` 只向无障碍报告状态，不改变外观。

填充式选中曾被尝试并否决：primary 的淡染落在灰上读不出"已选"，`primaryContainer` 在玫瑰色种子下读成警告、深色下是沉重的板，还会把成组行切成两半。

### 4.2 语义色

| 角色 | 值 |
|---|---|
| 错误 | `errorContainer` + `onErrorContainer`，无边框 |
| 成功 | 固定绿色板，忽略种子，tone 40（浅）/ 80（深） |
| 警告 | 固定橙色板，忽略种子，tone 40 / 80 |
| 成功/警告容器 | 容器色 90/30，`on` 色 30/90 |
| 延迟 | `< 600 ms` 成功，`≥ 600 ms` 警告，失败错误（`delayColor`） |
| 破坏性操作（警告框内） | `ColorScheme.destructive` = iOS 27 systemRed：浅 `#FF383C` / 深 `#FF4245` |
| 菜单危险项 | `error`（非 destructive） |
| 数据系列 1 | `primary` |
| 数据系列 2 | `primary` 45% 叠在卡片色上（`DashboardColors.series2`） |

`success` / `warning` **不**做种子和声化——在玫瑰色种子下橙色会被拉向红色，直到警告与错误分不开。

### 4.3 文字与标记

- 标题与数值：`onSurface`
- 标签与次要行：`onSurfaceVariant`
- `outline` **不是**文字色；数值永不被染成 primary
- 只做识别的标记（国旗、组图标、服务图标、来源头像）落在中性井上，用 `onSurfaceVariant`
- `primaryContainer` / `secondaryContainer` / `tertiaryContainer` **既不是表面填充也不是选中填充**，不得出现在卡片、行、标记或导航指示器上

### 4.4 遮罩

| 名称 | 值 |
|---|---|
| `modalScrim` | `scrim` @ 32% |
| `alertScrim` | 浅色 20% / 深色 48%（对齐 iOS） |

模态一律用遮罩压暗，**永不模糊背景**。每个模态自带遮罩，包括从另一个模态里打开的。

### 4.5 分隔线

唯一的结构线是 `onSurface` @ **14%**，永不 `outlineVariant`（后者在深色页面上对比度翻倍，还带种子色调）。半透明让同一条线在页面、卡片、chrome 上都保持同样的对比度。

---

## 5. 形状系统

### 5.1 圆角刻度

`lib/ui/shape.dart`：

```dart
none 0 · xs 4 · sm 8 · md 16 · lg 20 · xl 24 · xxl 28 · alert 34 · full 1000
```

**按组件最短边选档**（`AppCorner.fit()` 取不超过短边 1/3 的最大档）：

| Token | 值 | 最短边 | 用途 |
|---|---|---|---|
| `none` | 0 | — | 方角、成组行的平边 |
| `xs` | 4 | — | 已在圆角面内的瓷砖 |
| `sm` | 8 | ≤ 48 | chip、滑块、色板、小方瓷砖 |
| `md` | 16 | ≤ 64 | 输入框、菜单、按钮、弹窗 |
| `lg` | 20 | 64–180 | 中型卡片：仪表盘瓷砖、代理节点卡 |
| `xl` | 24 | 通栏 | 列表行、成组行、组头、个人资料卡 |
| `xxl` | 28 | > 200 | sheet、全屏容器 |
| `alert` | 34 | — | iOS 警告框 |
| `full` | 1000 | — | 胶囊与圆形：按钮、轨道、指示器、进度、头像 |

**为什么 `xl` 与 `lg` 并存**：一个 token 无法服务两个尺寸类。通栏面又宽又矮，角必须比高度暗示的更大，否则不再读作卡片。`xl` 是唯一刻意超出 1/3 规则的档位——值仍小于标准行高的一半，所以不会被钳制，所有通栏面渲染出同一个圆角。

**嵌套圆角是推导值，不是 token**：同心圆角要求 `outer = inner + inset`，所以命名内缩量并计算外值（`lib/ui/popup.dart` 的 `_cardRadius = _itemRadius + _cardInset`）。

**超出刻度的组件保留自己的值**，在文件内命名一次：侧边栏 `_itemShape = AppShape.all(10)`（介于 chip 尺寸的 `sm` 与近胶囊的 `md` 之间，用于 40 dp 高的行）。

### 5.2 超椭圆强制

`test/lint/superellipse_corners_test.dart` 在 `lib/` 中禁止所有圆形圆角 API。逐层替代：

| 层 | 禁止 | 使用 |
|---|---|---|
| 形状 | `RoundedRectangleBorder` | `RoundedSuperellipseBorder` |
| 裁剪 | `ClipRRect` | `ClipRSuperellipse` |
| 装饰 | `BoxDecoration(borderRadius:)` | `ShapeDecoration(shape: RoundedSuperellipseBorder(...))` |
| 画布 | `drawRRect` | `canvas.drawRSuperellipse` |
| 墨水 | `InkWell(borderRadius:)` | `InkWell.customBorder: AppShape.*` |
| 胶囊 | `StadiumBorder` | `AppShape.full` |

`StadiumBorder` 用曲率跳变连接圆头与直边；`AppShape.full`（全半径超椭圆）平滑过渡，且在正方形上渲染出精确的圆，所以它同时充当圆形。

Material 3 默认把每个按钮、图标按钮、分段按钮、搜索栏都设为 `StadiumBorder`；`ThemeData.withAppShapes` 替换了这些主题。本地 `FilledButtonTheme` 会覆盖应用级设置，所以必须合并环境样式：

```dart
style: FilledButton.styleFrom(...).merge(FilledButtonTheme.of(context).style)
```

### 5.3 自写输入框描边

`AppInputBorder` 替代包里的 `ShapedInputBorder`：后者的浮动标签缺口是一条沿顶边的平带，任何 ≥ 8 的圆角都会被它削平。`AppInputBorder` 裁剪缺口区域并在其中绘制完整超椭圆，如同 Flutter 自带的 `OutlineInputBorder` 缩短弧线。它不需要 `contentPadding` 补偿。

### 5.4 主题级形状映射

`ThemeData.withAppShapes` 统一设定：

| 组件 | 形状 |
|---|---|
| Card | `md` |
| Dialog | `alert`（34） |
| BottomSheet | 顶部 `xxl` |
| SnackBar | `xs` |
| Chip | `sm` |
| PopupMenu / Menu | `md` |
| InputDecoration | `AppShape.input` |
| FAB | `md` |
| NavigationBar / Rail 指示器 | `full` |
| ProgressIndicator | `full` |
| Filled / Text / Outlined / Elevated / IconButton | `full`（胶囊） |
| Tooltip | `xs` + 自定义 `ShapeDecoration` |

---

## 6. 字体系统

### 6.1 刻度

`AppTextStyle`（`lib/ui/typography.dart`）= **Apple iOS Dynamic Type 在默认尺寸下的完整刻度**，dp 即 pt，外加两档 Apple 没有的：

| 样式 | size | leading | 常规字重 | 强调字重 |
|---|---|---|---|---|
| `display` | 40 | 44 | w400 | **w700** |
| `largeTitle` | 34 | 41 | w400 | **w700** |
| `title1` | 28 | 34 | w400 | **w700** |
| `pageTitle` | 24 | 30 | **w600** | w700 |
| `title2` | 22 | 28 | w400 | **w700** |
| `title3` | 20 | 25 | w400 | w600 |
| `headline` | 17 | 22 | **w600** | w600 |
| `body` | 17 | 22 | w400 | w600 |
| `callout` | 16 | 21 | w400 | w600 |
| `subheadline` | 15 | 20 | w400 | w600 |
| `footnote` | 13 | 18 | w400 | w600 |
| `caption1` | 12 | 16 | w400 | w600 |
| `caption2` | 11 | 13 | w400 | w600 |

- 字重只有两档：常规与强调（标题 bold，其余 semibold）。**没有第三个字重。**
- `letterSpacing` 恒为 0。
- `display` 服务仪表盘的大数字，`pageTitle` 服务应用栏标题。

用法：

```dart
AppTextStyle.body.of(context)
AppTextStyle.headline.of(context, emphasized: true)
AppTextStyle.custom(context, size: 18, leading: 22)  // 按形状缩放的字，如图标里的字母
```

### 6.2 映射与强制

`ThemeData.withAppTypography` 把 Material 的 15 个 `TextTheme` 槽映射到该刻度，让 Material 组件跟随。**应用代码永不读那些槽**——`test/lint/typography_test.dart` 在 `lib/` 中除 `typography.dart` 外禁止出现 `textTheme`，也禁止 `lib/ui/` 之外的裸 `fontSize:` / `fontWeight:`（代码编辑器除外，它的字号跟随编辑器设置）。

### 6.3 标题位置

| 位置 | 样式 | 对齐 |
|---|---|---|
| 根页面、任何桌面页面 | `pageTitle`（24） | 起始边 |
| 手机视图的推入页 | `headline`（17） | 起始边 |
| 底部 sheet | `headline`（17） | 居中 |

标题控件（如编辑器的名称输入框）保持在起始边。返回按钮之后的标题在两侧都让出栏的动作内缩量。

### 6.4 字体族与回退

内置字体仅两个：

| 字族 | 用途 |
|---|---|
| `JetBrainsMono` | 代码编辑器 |
| `Twemoji` | `EmojiText` 逐段拆分 emoji 到该字族，避免系统 emoji 字体不一致 |

CJK 回退（`lib/ui/font.dart`）按平台与语言选择：

| 平台 | 简体 | 繁体 | 日文 | 韩文 |
|---|---|---|---|---|
| Windows | Microsoft YaHei UI | Microsoft JhengHei UI | Yu Gothic UI / Meiryo UI | Malgun Gothic |
| macOS | PingFang SC | PingFang TC | Hiragino Sans | Apple SD Gothic Neo |
| Linux | Noto Sans CJK SC 等 | Noto Sans CJK TC 等 | Noto Sans CJK JP 等 | Noto Sans CJK KR 等 |

**理由**：系统回退按 UI locale 选汉字字形，英文界面下 Windows 会把中文代理名画成日文字形。

### 6.5 文本缩放

| 常量 | 值 |
|---|---|
| `maxTextScale` | 1.4 |
| `minTextScale` | 0.8 |

两个扩展方法（`lib/ui/metrics.dart`）：

```dart
num.ap(BuildContext)   // 完全跟随文本缩放
num.mAp(BuildContext)  // 只放大不缩小
```

`_adaptiveScaleOf = 1 + (textScaler.scale(1) - 1) * 0.5`——尺寸只跟随缩放量的一半，避免大字号下布局崩坏。

### 6.6 文本工具

| 组件 | 作用 |
|---|---|
| `TooltipText` | 测量后仅在真正溢出时才挂 `Tooltip`（长按触发，显示在上方） |
| `TooltipLabel` | `TooltipText` + 省略号，默认 2 行 |
| `EmojiText` | 拆分 emoji 到 Twemoji 字族 |
| `.toLight` / `.toLighter` | 颜色 80% / 60% |
| `.toTabular` | 等宽数字（`FontFeature.tabularFigures()`），仪表盘所有数值都用 |
| `.toJetBrainsMono` | 切到等宽字族 |

`Measure` 类（`lib/ui/measure.dart`）缓存文本测量（上限 512 条），提供 `listTitleHeight`、`listSubtitleHeight`、`cardHeaderHeight`、`listRowHeight`、`listTwoLineRowHeight`，让行高随字体与缩放自适应。

---

## 7. 图标系统

### 7.1 字形基础

**121 个字形，零 Material 图标字体**。`test/lint/material_icons_test.dart` 在 `lib/` 中禁止任何 `Icons.*` / `AnimatedIcons.*`。

`Glyph`（`lib/ui/icons/glyph.dart`）：

```dart
static const size = 24.0;        // 网格
static const strokeWidth = 1.8;  // 描边
```

- 形状列表定义在 24 单位网格上，圆头圆角，超椭圆拐角。
- 原始形状画不出的曲线用 `GlyphPath`——沿描边中心线追踪的 SVG 路径数据。

### 7.2 三种形状角色

每个形状有角色，决定其填充形态（如同 SF Symbols 与 Fluent 为每个描边图标配一个填充变体）：

| 角色 | 行为 |
|---|---|
| `body` | 向内填充，最多达其较短边的一半 |
| `detail` | 从填充中挖空；`solid` 型 detail（如圆点）按面积挖 |
| `line` | 始终是描边；填充时从 1.8 加粗到 **2.2** |

`GlyphPainter` 可绘制两形态之间的任意点。细节在前半程变细、后半程作为挖空返回——所以细节**永不**同时是描边又是挖空。

字形以不透明方式画进一个携带颜色 alpha 的图层：半透明色（如禁用态）在描边穿过填充处仍保持均匀，挖空只清除字形自己的像素。

### 7.3 组件

| 组件 | 作用 |
|---|---|
| `GlyphIcon` | 固定 `fill` 显示字形；尺寸、颜色、填充默认取 `IconTheme`；`Glyph.matchTextDirection` 时在 RTL 下镜像 |
| `AnimatedGlyph` | 由 `filled` 标志驱动，填充时短暂弹跳（`sin(πt) × 0.1`），**350 ms** `easeInOutCubic` |
| `AppGlyphs.sidebar(progress)` | 形状跟随其他状态的 builder，如随面板宽度变化的侧边栏开关 |
| `CaptionIcon` | 唯一不遵循字形网格的图标：窗口标题按钮保持 Windows 10 像素对齐的 1 px 描边 |

### 7.4 填充规则

| 场合 | 填充 |
|---|---|
| 默认 | 描边 |
| `IconButton.filled` / `.filledTonal` | `fill: 1` |
| `FilledButton`（tonal 与 icon 形式） | `fill: 1` |
| 其他场合 | 填充**只表示状态**：选中的目的地、固定的窗口、点亮的手电筒 |

理由：描边在着色容器上比容器本身读起来更弱。没有 body 的字形只加粗线条，仍取 `fill: 1`，所以规则在字形日后获得 body 时依然成立。

`test/lint/filled_button_glyphs_test.dart` 检查每个写在实心按钮内的 `GlyphIcon`。

**唯一例外**：手机的浮动导航栏——像 iOS tab bar 一样填充每个目的地，只用颜色标记选中。

### 7.5 字形清单

| 类别 | 名称 |
|---|---|
| 导航目的地 | `dashboard` `proxies` `profiles` `requests` `connections` `dns` `resources` `logs` `tools` `target` |
| 方向 | `arrowBack` `chevronBack` `chevronForward` `chevronDown` `chevronUp` `picker` `caretDown` `arrowUp` `arrowDown` `openExternal` |
| 编辑 | `undo` `redo` `edit` `compose` `delete` `copy` `cut` `paste` `selectAll` `deselect` `dragHandle` `sort` `listAdd` `clearAll` |
| 数据 | `sync` `refresh` `reset` `export` `upload` `add` `remove` `addCircle` `removeCircle` `close` `check` `checkDouble` `checkCircle` `block` `matchCase` `regex` `search` `filter` `textShort` `wrap` |
| 状态 | `info` `warning` `error` `more` `moreCircle` `status` `verified` `sparkle` `bolt` `star` |
| 网络 | `vpn` `wifi` `wifiOff` `link` `send` `cloudDownload` `cloudSync` `swap` `shuffle` `split` `speed` `dataUsage` `memory` `cpu` `devices` `locate` |
| 配置 | `rules` `rulePresets` `code` `settings` `sliders` `wrench` `toggle` `pin` `layers` `puzzle` `appsList` `iconTile` `importFile` |
| 外观 | `sun` `pureBlack` `palette` `paintbrush` `eyedropper` `motion` `blur` `liquidGlass` `font` `textSize` `language` `keyboard` |
| 安全 | `lock` `lockOpen` `key` `password` `account` `eye` `eyeOff` `gavel` |
| 硬件 | `photos` `camera` `qrCode` `torch` `torchOff` `torchAuto` `broom` |
| 其他 | `clock` `customize` `text` `blur` `star` |

命名规则：按应用中的语义命名，一个字形一个含义，以 SF Symbols 和 Fluent 为隐喻参考。测试用 `find.byGlyph`（`test/helpers/glyph_finders.dart`）查找。

---

## 8. 动效系统

### 8.1 弹簧参数全表

项目几乎不用线性时长动画，而是真实弹簧物理。

#### ElasticPress（全局按压语汇）

| 名称 | 参数 |
|---|---|
| 按下膨胀 | mass 1, stiffness 311, ratio 0.593 |
| 跟随手指 | mass 1, stiffness 1024, ratio 0.9 |
| 轨道跟随 | 120 ms |

#### 导航 Dock

| 名称 | 参数 |
|---|---|
| `_trackSpring` | 120 ms |
| `_pressSpring` | mass 1, stiffness 311, ratio 0.593 |
| `_followSpring` | mass 1, stiffness 1024, ratio 0.9 |
| `_liftSpring` | 250 ms |
| `_travelSpring` | 400 ms, bounce 0.15 |
| `_jellySpring` | mass 1, stiffness 100, ratio 0.4 |
| `_settleSpring` | 500 ms, bounce 0.32 |
| `_hoverSpring` | 260 ms, bounce 0.18 |
| `_fadeSpring` | 200 ms |
| `_glowInSpring` | 100 ms |
| `_glowOutSpring` | 500 ms |

#### 弹出菜单

| 名称 | 参数 |
|---|---|
| `_growSpring` | 500 ms, bounce 0.3 |
| `_moveXSpring` | 520 ms, bounce 0.2 |
| `_moveYSpring` | 340 ms, bounce 0.12 |
| `_returnSpring` | 260 ms |
| `_returnXSpring` | 220 ms |
| `_returnYSpring` | 290 ms |
| `_unfoldSpring` | 420 ms, bounce 0.15 |
| `_foldSpring` | 340 ms |

#### 其他

| 名称 | 参数 |
|---|---|
| 警告框 | 275 ms，总时长 404 ms（临界阻尼，UIKit 0.404 s 落定） |
| 排序拖动 | `SpringDescription.withDurationAndBounce` |
| Sheet 展示 | mass 3 / stiffness 1000（临界阻尼，ω ≈ 18.26 rad/s，约 0.5 s） |

### 8.2 时长表

| 场景 | 时长 |
|---|---|
| 页面转场 | 450 ms |
| 桌面页面淡入 | 200 ms |
| 侧边 sheet 进 / 出 | 300 / 200 ms |
| 底部 sheet 展示 | ~500 ms |
| 弹出菜单开 / 关 | 750 / 300 ms |
| 菜单收缩 | 200 ms |
| 警告框 | 404 ms |
| 分段控件指示块 | 250 ms |
| 分段控件文字 | 200 ms |
| 侧边栏展开 | 250 ms |
| 侧边栏指示条 | 320 ms |
| 导航可见性切换 | 300 ms |
| 折叠区段展开 | 240 ms |
| 空状态入场 | 900 ms（三段交错 60 ms） |
| 空状态退出 | 150 ms |
| 字形填充 | 350 ms |
| 骨架脉动 | 1600 ms |
| 环形加载一周 | 1000 ms |
| 滚动条淡出 | 250 ms |
| 滚动条停留 | 1200 ms |
| 滚动条强调 | 120 ms |
| 文本放大镜进出 / 跟随 | 150 / 45 ms |
| Dock 光晕 进 / 出 | 120 / 360 ms |
| 连接选择过渡 | 240 ms |
| 主题交叉淡入 | `kThemeAnimationDuration` |

### 8.3 曲线词汇

`Easing.*`（Material 3 曲线，来自 `material_ui`）：

```
standard · standardDecelerate · standardAccelerate
emphasizedDecelerate · emphasizedAccelerate · legacyDecelerate
```

`Curves.*` 使用频次：`easeOutCubic`（19）· `easeOut`（16）· `easeOutBack`（14）· `easeInOutCubic`（9）· `easeInOut`（5）· `easeInCubic`（5）· `easeIn`（5）· `fastOutSlowIn`（2）· `fastEaseInToSlowEaseOut`（2）· `easeInOutCubicEmphasized`（1）· `easeInBack`（1）。

### 8.4 ElasticPress：统一按压语汇

所有按钮、卡片、警告框共用的弹性按压组件（`lib/ui/navigation_dock.dart`）：

```dart
ElasticPress(
  enabled: true,
  pullPastEdges: false,   // 手指越界时是否向手指方向拉伸
  maxGrowth: 16,          // 最大膨胀 dp
  maxPull: null,
  panelOf: null,          // 盒子大于面板时，面板在其中的位置
  child: ...,
)
```

行为：

1. **按下** → `_lift.springTo(1, _pressSpring)`，从按压点膨胀，最大 **16 dp**（即尺寸的 1/8）。
2. **移动** → `_pullToward(position)`，手指越界时向手指方向拉伸。
3. **释放** → 在按压弹簧上弹回。

`ElasticPress.maybeOf(context)` 让弹窗、菜单、`TonalButtonGroup` 把指针事件转交给外层的弹性按压，从而"从按钮按下、滑入菜单"是一段连续的动效。

`ElasticPress.paintBoundsOf(box)` 计算盒子在周围按压变换下的绘制位置，供菜单在按压膨胀时正确命中测试。

### 8.5 弹性滚动

```dart
double _rubberBand(double overshoot, double limit) {
  final pull = 1 - 1 / (overshoot.abs() * 0.55 / limit + 1);
  return limit * pull * overshoot.sign;
}
```

`_overdrag = 0.35`、`_pullLimit = 7/32`、`_pullStretch = 0.5`。

### 8.6 关键动效详解

#### 弹出菜单（`lib/ui/popup.dart`）

严格复刻 WWDC25 描述的 iOS 26 菜单：

- **从控件长出**时保持两者连续：关闭时**沿同一托盘返回控件**，内容先模糊掉（`_maxContentBlur = 8`）。
- **从某点打开**（上下文菜单）没有可返回的控件，于是向该点缩小到 `_dismissScale = 0.8` 同时淡出。
- **子菜单**：一个弹簧（`_unfold`）同时移动其表面与裁剪、让父级后退、揭示条目。中途折返会带着当时速度掉头。父级的条目藏在子菜单表头下，子菜单站到该条目上之后才被移除。
- **托盘**：从按钮的 `sourceColor` 起，展开时按钮本身隐藏，托盘内绘制按钮快照（`sourceImage`）。
- **按压识别**：`CommonPopupBox` 在按钮的 pointer down 上打开（如 iOS 中菜单是按钮主操作时），按压延续进菜单。`_PopupPressRecognizer` 在那时接管指针，按钮自己的 tap 与 tooltip 长按退出。在滚动视图里先等 `kPressTimeout`（如 iOS 滚动视图对触摸的延迟），所以从按钮拖动能滚动，更快的点击在释放时打开。
- **行按压**：`_RowPressRecognizer` 在手指滑动时高亮指下的行（在菜单绘制的位置做命中测试），每次变化发一次选择点击音效，在释放的行上拾取。
- **悬停展开**：在带子菜单的条目上停 `_dwellDuration = 400 ms` 展开，在已展开子菜单的表头上停则折回，每次高亮移动发一次点击音效。

#### 底部 Sheet（`lib/ui/snap_sheet.dart`）

`SnapSheetRoute` 复刻 `UISheetPresentationController`，参数是在 iOS 27 模拟器上逐帧记录 presentation layer 拟合的：

```dart
const snapSheetDetents = [9 / 16, 0.9];
const _stiffness = 1000 / 3;          // UIKit mass 3, stiffness 1000
final _omega = sqrt(_stiffness);      // ≈ 18.26 rad/s
const _thrownRatio = 0.8;             // 抛出时阻尼比
const _projectionTime = 0.99 / (1 - 0.99) / 1000;  // DecelerationRate.fast
const _settleTolerance = Tolerance(distance: 0.5, velocity: 10);
```

行为：

- 一个弹簧移动 sheet：展示、关闭、detent 变化、回弹都在 1 pt 内跟随它。
- 朝目标抛出时保留速度并以约 0.8 阻尼比轻微过冲；反向抛出则丢失速度、硬着陆。
- 释放落在离 `DecelerationRate.fast` 投影最近的那个 detent 或关闭。
- 无速度时，放过最短 detent 的一半即关闭；1000 pt/s 的下甩在 iOS 上回弹，1500 pt/s 才关闭。
- 手指在最短 detent 以下 1:1 跟随，到最高 detent 停止，**不拉伸**。
- 内容上：向上拖先长高 sheet，剩余量滚动内容，一个手势完成。向下拖只在内容处于顶部时移动 sheet；把内容滚回顶部就停在那里（iOS 弹一下而不是交给 sheet）。
- **键盘抬起 sheet 的脚**：detent 保持其内容在键盘之上，直到顶部留出一个 peek。sheet 高度是 inset 的函数，所以它逐帧跟随键盘并落回原处。
- **键盘开始/停止承载 sheet 的位置，其函数做了圆角**（`_rounded`），避免 sheet 在键盘滑过短内容的空白时静止然后跳变；`_peakEase = 120`。

`_SheetModel` 把 sheet 当作一个活动：静止在 detent、保持在某高度、或作为相对 detent 的偏移沉降。布局每帧从视图、键盘、内容重新解出 detents，所以静止或沉降中的 sheet 会自己跟随页面转场的尺寸变化或键盘，无需存储高度再打补丁。

#### 页面转场（`lib/ui/page_transition.dart`）

`SharedXPageTransition`，由 `PagedSheetRoute` 与 `CommonRoute` 共用：

```dart
static const duration = Duration(milliseconds: 450);
static const pageSlide = Offset(0.25, 0);                    // 页面宽度的 1/4
static final _slideCurve = CurveTween(curve: Curves.easeInOutCubicEmphasized);
static const _fadeInCurve = Interval(0.15, 0.55, curve: Curves.easeOutCubic);
static const _fadeOutCurve = Interval(0, 0.3);
```

淡出不用 opacity layer：上层的页面在两条淡变交叉点自己那一侧才显示，另一侧不透明覆盖，所以两层互不透出。不随之淡出的下层页面（如 `CommonRoute` 下的 home route）由上层页面"绘制 surface 覆盖"来淡化。

`CommonDesktopRoute` 用普通 **200 ms** `FadeTransition` 原地淡入。

`DeferredPushRouteMixin` 把 push 转场的启动推迟一帧，让较长的首次构建不吞掉转场的开头。

#### 警告框（`lib/ui/dialog.dart`）

```dart
const _alertDuration = Duration(milliseconds: 404);
const double _alertEntryScale = 1.2;    // 从 1.2 倍缩入
final _alertSpring = SpringCurve(
  SpringDescription.withDurationAndBounce(duration: Duration(milliseconds: 275)),
  seconds: 0.404,
);
```

- 从 **1.2 倍**缩放进入，只淡出（对齐 UIAlertController）。
- 按压时整个警告框（玻璃与内容）膨胀，实测在 320 宽上为 **4 pt**；拖出边缘拉伸约 **3 pt**。指下的按钮不膨胀，只把填充淡到 **40%**（深色默认按钮 50%），深色普通按钮保持填充。
- `ElasticPress` 通过 `panelOf` 找到表面，因为 `AlertDialog` 铺满屏幕。

#### 列表增删

`AnimatedEntries` / `KeyedAnimatedList`（`lib/ui/keyed_animated_list.dart`）：

- 每项独立控制器，所以一项还在动时另一项的变化不会打断它。
- `SizeTransition`（`easeInOutCubic`）+ `FadeTransition`（`Interval(0.5, 1)`）。
- `grouped` 模式下，折叠出去的子项在圆角行序列中保留其位置，其余行围着它合拢（`ItemPosition`）。
- `KeyedAnimatedList` 用**一个控制器**驱动一次更新中所有进出行的动画，即使刷新触及屏幕外的行也只跑一个 ticker。

#### 折叠区段（`lib/ui/collapsible_section.dart`）

`UnfoldEntryTransition`：

```dart
static const duration = Duration(milliseconds: 240);
static const _fade = 0.5;
// curve: Curves.easeOutCubic / reverse easeInCubic
```

条目从表头下展开，**离屏幕可容纳条目中点最近的最先淡入**。这是唯一的顺序：猜错只改变谁领舞，不改变最终显示什么。

#### 字形填充（`AnimatedGlyph`）

```dart
const _fillDuration = Duration(milliseconds: 350);
const _popScale = 0.1;
// 填充曲线 Curves.easeInOutCubic
// 弹跳 pop = sin(πt) × 0.1
```

#### 主题切换（`lib/ui/theme_cross_fade.dart`）

一帧内切换主题，把上一帧截图在顶层淡出，而不是逐帧 lerp `ThemeData` 并重建每个依赖者。

- 截图在 `didChangeDependencies` 中取（`toImageSync`）。
- 用 `dstOut` 清掉当前帧对应区域，再用 `plus` 把快照加回，混合模式让**半透明窗口保持其不透明度**（`srcOver` 会把它抬高）。

#### 连接按钮（`DashboardConnectedButton`）

M3 Expressive connected buttons：两端圆化整组、内侧角保持紧凑、选中的那个变成完整胶囊。选中过渡 **240 ms** `easeOutCubic`，圆角与颜色同时插值。

#### 其他

- **弹性缩放进入**：`FadeScaleEnterBox`，300 ms，缩放 0.4 → 1.0。
- **滑入进入**：`FadeSlideEnterTransition`，24 dp 位移，`emphasizedDecelerate`，淡入区间 `Interval(0, 0.25)`。
- **淡入缩放**：`FadeScaleEnterTransition`，缩放 0.70 → 1.00，`legacyDecelerate`。
- **文字放大镜**：`TextLoupe`，80 × 47.5 dp，胶囊用超椭圆绘制，进出 150 ms / 跟随 45 ms。
- **拖拽排序**：`commonProxyDecorator` 在拖动时把卡片放大到 1.02，`Curves.easeInOut`，同时把圆角从 `none` 渐变到 `xl`。

### 8.7 触觉反馈

`HapticFeedback.selectionClick` 只用在三处：

| 位置 | 时机 |
|---|---|
| `popup.dart`（2 处） | 菜单条目滑动高亮切换、子菜单展开/折回 |
| `navigation_dock.dart`（2 处） | Dock 目的地切换 |
| `scroll.dart`（1 处） | 滚动条抓取 |

### 8.8 降低动态

```dart
// lib/ui/context.dart
bool get disableAnimations => MediaQuery.disableAnimationsOf(this);
Duration motionDuration(Duration duration) =>
    disableAnimations ? Duration.zero : duration;
```

检查此标志的地方：`FadeScaleEnterBox`、`SkeletonPill`、`ThemeCrossFade`、`AnimatedEntries`、`NullStatus` 入场、`DashboardConnectedButton`、`current_outbound` 装饰变形、`SnapSheet` 等。

`NullStatus` 的入场编排还会在 `isRouteArriving(context)` 时跳过——页面转场期间不叠加第二段动效。

---

## 9. Liquid Glass 材质

`lib/ui/liquid_glass.dart` + `shaders/liquid_glass.frag`（477 行 GLSL）。

### 9.1 模式

```dart
enum LiquidGlassMode { none, standard, ultra }   // 默认 none
enum LiquidGlassQuality { standard, ultra }
```

开启后以下元素变成玻璃而非填充：Dock、页面栏的按钮、sheet 内停靠的搜索框、弹出菜单卡片、警告框。仪表盘上只有启动按钮是玻璃面板——已连接的按钮和开关保持填充，因为每块玻璃都会把它的区域加入一张在仪表盘重绘时重新采集的截图，而仪表盘重绘很频繁。

### 9.2 六种材质

| 材质 | 用途 | 关键参数（浅色） |
|---|---|---|
| `regular` | 按钮 | tint `#8AFDFDFD`、contrast 0.4、saturation 2.08、rim 0.28、rimWidth 1.2、hairline 0.25 |
| `pill` | Dock | blur 0、haze 0.72 + hazeBlur 15.6、saturation 2.1、hairline 0.35 |
| `menu` | 菜单 / 弹出 | blur 12、saturation 2、brightness 1.1、rim 0 |
| `alert` | 警告框 | menu 的 blur 提到 16 |
| `tinted` | 选中按钮 | tint 用 primary @ 82%、saturation 1.2、hairline 0.34 |
| `lens` | Dock 透镜 | refraction −4.3、bevel 5.5、dispersion 6.5、rimBlur 1.5、hairline 0.42 |
| `lensContent` | 透镜内容 | refraction 14、bevel 11.2、dispersion 3.6、rimBlur 2.5 |

深色模式各有对应参数：`regular` tint 用 `surfaceContainerHighest` @ 62%、contrast −0.4、saturation 2.84；`pill` tint @ 48%；`menu` tint @ 63%、brightness 1.8。

### 9.3 技术实现

- **`LiquidGlassHost`** 覆盖整个应用，在帧被合成时、子级之前，把每块面板会读到的背景重绘到一张小图：该面板之前的图层、在它的触达范围内、缩小到 frost 的 sigma 只跨两个像素，最粗不超过 8 屏幕像素对 1，frost 由引擎模糊进去。
- **每块面板以普通 paint 读它那部分图像**：frost 均匀处一次采样。没有面板结束渲染 pass，没有拷贝表面，**玻璃永不落后内容一帧**。
- **层级**：在同一个 root 中画在更早面板之上的面板读第二张图（上一级），把第一个作为玻璃画进去。"之上"指在 frost 的 2.5 sigma 内且在其弯曲范围内——栏按钮相距 8 dp，落在溢出按钮的槽内，会升一级并每帧多花一张图。
- **缓存**：一个层级在它绘制的内容（同样的图、同样的变换、裁剪、效果）不变时保留其图像，所以静止屏幕重绘玻璃无需采集，内容在玻璃下滚动时每帧采集一次。图像内每个模糊相同的槽带以同样方式保留像素，从上一张图拷贝而非重绘重模糊。
- **不在 Skia 上运行**：那里一张图只要活着就保留其绘制来源，每次从上一张拷贝会持有此前每一张，内存每帧增长一次采集。
- **不均匀 frost** 处（沿软化边缘），着色器把采集没有的部分按引擎的高斯补上：设备像素上的 Vogel 盘，16 次采样到 sigma 2、32 次到 3.5、超过则 64 次。
- **光学**用圆形斜角，宽度与深度会缩小以适配小面板，所以按钮的弯曲与 Dock 成比例。材质取自 iOS 27（codenameone/CodenameOne#5906 拟合）：`pill` 用于 Dock，`regular` 用于按钮。背景以 `offset + scale * saturated backdrop` 穿过，tint 在 `1 - scale` 处携带偏移。
- **rim**：`menu` 玻璃把边缘交给 `LiquidGlassRim`——画布上在面板外的线，以及着色器中的高光，按带与轮次绘制。暗线在玻璃之外，两侧最深；高光作为光加到玻璃上，顶底中部最亮，约在圆角开始时减半、结束时剩五分之一、沿两侧消失，向内约 5 dp 内淡出。
- **色散**：透镜的红色与蓝色沿顶底最多分开 6.5 dp，两端约 3.9 dp。
- **`standard` 档**刻意省略最贵、收益最低的部分：haze 变成整个 frost（Dock 用一张缩小槽代替全分辨率清晰槽，文字不再隐约透出）；透镜的 rim 不分解颜色；抬起透镜的拷贝不弯曲，只染上其面板会加的光；Dock 不投影（低于 3/255）。

### 9.4 性能预算

文档记录了 SwiftShader 模拟器（1080×2400 @ 420 dpi）上的实测：

| 操作 | 无玻璃 | `ultra` | `standard` |
|---|---|---|---|
| 滚动 Tools | 43 ms | +16.4 | +9.5 |
| 拖动透镜 | 52 ms | +40 | +17 |
| 菜单下滚动 | 34 ms | +26 | +18 |
| 打开菜单 | 22 ms | +13 | +11 |
| 关闭菜单 | 17 ms | +9 | +5 |

每帧渲染 pass：滚动 18.8（两档）对 23.6；透镜 `ultra` 22 / `standard` 18 对 30。

这些数字是软件渲染器上的，只用于给采集排序而非定价；没有手机实测过。

---

## 10. 布局与响应式

### 10.1 断点

`lib/common/layout.dart`：

```dart
const maxMobileWidth = 600;
const maxLaptopWidth = 840;
```

```dart
ViewMode getViewMode(double viewWidth) {
  if (viewWidth <= maxMobileWidth) return ViewMode.mobile;
  if (viewWidth <= maxLaptopWidth) return ViewMode.laptop;
  return ViewMode.desktop;
}
```

- 侧边栏**仅 desktop 可展开**（laptop 展开会让内容比低一档的移动布局还窄），所以标签等到 desktop 才出现。
- 默认窗口 **1280 × 880**。

### 10.2 栅格

| 项目 | 规则 |
|---|---|
| 代理列数 | `max(ceil(width / 250), 2)`，再按 `tight` +1 / `standard` 0 / `loose` −1 |
| 代理卡片间距 | 8 dp |
| 个人资料卡最小宽 | 300 dp，最多 3 列 |
| 配置卡片间距 | mobile 8 / laptop 12 / desktop 16 |
| 仪表盘列宽上限 | 440 dp |

### 10.3 尺寸常量

```dart
const listRowMinHeight = 56.0;
const listRowVerticalPadding = 10.0;
const pageToolbarHeight = 72.0;
const sheetToolbarHeight = 48.0;
const sheetAppBarHeight = 68.0;
const captionButtonWidth = 46.0;   // Windows 11 标题按钮
const pinIconSize = 16.0;
```

### 10.4 窗口 chrome

| 平台 | 标题栏高 | 说明 |
|---|---|---|
| macOS | 28 | 红绿灯区域 78 × 32（macOS 27 实测：9 到 69） |
| Windows | 32 | 标题按钮 46 宽 |
| 其他桌面 | 32 | |

macOS 10 及更早保留其原生标题栏；移动视图把红绿灯放进窗口头部，所以只有填满更宽窗口的页面才遇到它们。

---

## 11. 导航

### 11.1 目的地

`lib/views/navigation.dart` 定义九个目的地：

| 顺序 | 目的地 | 字形 | 可见模式 |
|---|---|---|---|
| 1 | Dashboard | `dashboard` | 全部 |
| 2 | Proxies | `proxies` | mobile + desktop（有代理时） |
| 3 | Profiles | `profiles` | 全部 |
| 4 | Requests | `requests` | `moreFull` |
| 5 | Connections | `connections` | desktop + `moreFull` |
| 6 | DNS | `dns` | `moreFull` |
| 7 | Resources | `resources` | `more` |
| 8 | Logs | `logs` | desktop + `more`（打开日志时） |
| 9 | Tools | `tools` | desktop + mobile |

### 11.2 桌面侧边栏（`lib/ui/sidebar.dart`）

| 常量 | 值 |
|---|---|
| `_compactWidth` | 48 |
| `_expandedWidth` | 220 |
| `_itemSize` | 40 |
| `_itemGap` | 4 |
| `_toggleGap` | 8 |
| `_verticalInset` | 4 |
| `_maxSideInset` | 12 |
| `_iconSize` | 24 |
| `_labelGap` | 12 |
| `_indicatorWidth` | 3 |
| `_indicatorHeight` | 16 |
| `_expandDuration` | 250 ms |
| `_indicatorDuration` | 320 ms |
| `_itemShape` | `AppShape.all(10)` |

- 窗口控制区（macOS 红绿灯）永不遮挡，面板永不比它窄。
- 无法原地加宽时（`onToggle` 为 null），开关打开一个**覆盖在内容上**的面板，带同样的填充与边缘，外加投影。
- 选中的目的地保持中性填充 + primary 指示条（颜色已标记它）。

### 11.3 手机浮动 Dock（`lib/ui/navigation_dock.dart`）

逐帧复刻 UIKit tab bar：

| 常量 | 值 | 含义 |
|---|---|---|
| `_barHeight` | 62 | Dock 高 |
| `_barPadding` | 4 | 内边距 |
| `_edgeMargin` | 21 | 边缘留白 |
| `_shadowRoom` | 8 | 投影空间 |
| `_hitSlop` | 8 | 命中扩展 |
| `_maxItemExtent` | 86 | 目的地宽 |
| `_minLensWidth` | 84 | 透镜最小宽 |
| `_fullWidthCount` | 5 | 满宽目的地数 |
| `_iconSize` | 28 | 图标 |
| `_labelGap` | 2 | 图标与标签间距 |
| `_labelInset` | 2 | 标签内缩 |
| `_labelSize` | 10 | 标签字号 |
| `_labelLeading` | 14.5 | 标签行高 |
| `_minLabelSize` | 9 | 标签最小字号 |
| `_pressGrowth` | 1/8 | 按压膨胀比例 |
| `_maxPressGrowth` | 16 | 按压膨胀上限 |
| `_lensGrowth` | 8 | 透镜比目的地宽的量 |
| `_lensMagnify` | 0.16 | 透镜放大率 |
| `_liftSnap` | 0.975 | 抬升吸附 |
| `_liftRelease` | 0.04 | 抬升释放 |
| `_hoverMagnet` | 0.2 | 悬停磁吸 |
| `_hoverParallax` | 0.5 | 悬停视差 |
| `_hoverAlpha` | 0.08 | 悬停透明度 |
| `_hoverSwell` | 0.4 | 悬停膨胀 |
| `_jellySpeed` | 3 | 果冻速度 |
| `_jellyStretch` | 0.2 | 果冻拉伸 |
| `_jellyBrake` | 25 | 果冻制动 |
| `_jellySquash` | 0.15 | 果冻压扁 |
| `_jellyAspect` | 1.4 | 果冻长宽比 |
| `_glowReach` | 28 | 光晕触达 |
| `_glowGrowth` | 3 | 光晕增长 |
| `_glowPeak` | 0.06 | 光晕峰值 |
| `_glowWash` | 0.7 | 光晕洗涤 |
| `_overdrag` | 0.35 | 过拖 |
| `_pullLimit` | 7/32 | 拉伸上限 |
| `_pullStretch` | 0.5 | 拉伸系数 |
| `_flingProjection` | 100 ms | 甩动投影 |

行为：

- 抬升 **16 dp**，把透镜内内容放大 **16%**。
- 抬升保持到它完全升起且透镜到位，所以点击会把它完整弹出。
- 目的地站在抬起的透镜上（如 iOS 绘制），所以透镜只弯曲 Dock 本身；透镜内的内容被拷贝、染色、放大到离屏层，第二块清晰面板再把它向边缘弯曲。
- 深色模式两块面板都把它显示的内容提亮 **1/3**，透镜向下投 **8 dp** 软阴影。
- 按压通过 `LiquidGlass.glow` 点亮 Dock：一片覆盖整个面板的洗涤，在手指处最亮，随淡出扩散。
- 标签超长时缩到 `_minLabelSize`，仍放不下则给 `Tooltip`。

**替代方案**：设置里关掉浮动栏，退回 Material `NavigationBar` + `AnimatedGlyph`，`AnimatedVisibility.bottomNavigation` 负责进出（300 ms）。

### 11.4 页面路由归属

| 场景 | 导航器 |
|---|---|
| 桌面每个目的地 | 各自的 `Navigator`（`_NavigationPage`） |
| 手机 | 无嵌套，全部走根导航器 |
| 对话框、通知 | 根 |
| 全应用闸门（如免责声明） | 根 |
| 无页面在后的工作（深链、托盘、热键、更新检查） | 根 |

**永不**从页面用 `rootNavigatorKey`、`globalState.navigatorKey`、`rootNavigator: true` 或 `useRootNavigator: true` 打开页面或 sheet，也永不从根上的东西（如对话框）的 context 推页面。

### 11.5 页面到达

- 加载在 widget 挂载时开始，**永不**放在 `whenRouteSettled` 之后。
- 直播信息流在页面移动时暂停刷新，静止后重放最新一次（`RouteMotionHoldMixin`）。连接、请求、日志、DNS 页与仪表盘的实时连接表都用它。
- 图像瓷砖滚动视图取 `arrivalScrollCacheExtent(routeSettled)`，视口外的行不在推入的同一帧构建。
- 等待数据的页面在全就绪前什么都不显示（`NullStatusSwitcher.isLoading`），可能跑很久的加载加上 `loadingStatus` 插画（400 ms 后才出现）。
- **永不**用骨架屏占位行——它得猜有多少行要来、逐块填充、重建它替代的每一行。`SkeletonText` 只用于已在屏幕上的卡片内的单个值。

---

## 12. 应用栏

`CommonScaffold`（`lib/ui/scaffold.dart`）最多在栏内保持 **3 个按钮**（含溢出菜单按钮）。

### 12.1 折叠规则

页面以数据形式交给它什么可以折叠：

| 参数 | 说明 |
|---|---|
| `primaryAction` | 主操作，永远在栏内 |
| `iconActions` | 可折叠的图标动作 |
| `menuItems` | 直接进溢出菜单 |
| `searchActions` | 搜索中显示 |
| `selectionActions` | 多选动作，永不折叠 |
| `confirmationAction` | 确认（保存），栏尾，永不折叠 |
| `actions` | 自定义 widget，**永不折叠** |

超过三个时，两个按钮留在溢出菜单旁：搜索按钮（搜索中则是清除按钮），然后是可折叠动作，主操作优先（除非 `foldPrimaryAction` 让图标动作排在前面）；其余全部进一个溢出菜单。

栏读作"两个一组的按钮组 + 单个按钮"。

**保存/确认**永远是 `confirmationAction` 里的勾，绝不在 `iconActions` 里；只有表单 sheet 会把它移到脚部。

**标题菜单**（`titleMenuItems`）：标题显示箭头并打开它们，如 iOS 文档标题菜单。菜单从标题的**起始边**打开（`PopupPlacement.overAnchorStart`），因为栏自己的菜单从结束边打开会盖住返回按钮。

### 12.2 尺寸

```dart
const double appBarActionSpace = 4;      // 图标动作间距
const double _barButtonGap = 8;          // 按钮组间距
const double _headerOverhang = 16;
const double _iconButtonTapPadding = 4;
double appBarActionInset(bool compact) => compact ? 16 : 20;   // iOS 布局边距
double _iconActionInset(bool isMobileView) => isMobileView ? 4 : 8;
```

`TonalButtonSize`：

| 尺寸 | 按钮 | 图标 |
|---|---|---|
| `bar` | 44 | 24 |
| `section` | 36 | 20 |
| `compact` | 32 | 20 |

### 12.3 栏按钮

- 都是带 tooltip 的字形按钮，包括全选。
- 唯一的文字按钮是自定义配置的"导入"——一个填充按钮，其 `CommonPopupBox` 打开导入菜单。
- 选中状态用实心 primary 背景 + `onPrimary` 前景（玻璃模式下不设背景色，交给玻璃）。
- 加载中显示 `CommonCircleLoading`，尺寸等于按钮图标。

### 12.4 Sheet 的栏

- 一个 sheet 页面是被推入的路由，穿戴页面的 chrome。
- 底部 sheet 把栏浮在主体之上，搜索从**停在脚部的输入框**发起，而不是占满栏的按钮。
- 关闭按钮领衔栏（当栏有动作时），除非旁边有主操作——那时两者作为一组结束栏。
- sheet 盖住页面时，下层栏的动作**淡出**（`sheetCoverOf` 读该入场）。

---

## 13. 列表与设置行

### 13.1 行规格

```dart
const listRowMinHeight = 56.0;
const listRowVerticalPadding = 10.0;
```

- 水平内边距 16 dp。
- 成组行用 `AppCorner.xl` 只圆首尾两端，中间为 `none`——形成连续的"成组跑道"（`DecorationListItem` + `ItemPosition`）。
- 行分隔线从标题起点画到 **14 dp** 内缩处，**高亮时本行与相邻行都不画**（如 iOS 分组列表）。
- 分隔线粗细按设备像素计算：`round(dpr × 0.75) / dpr`，至少 1 物理像素。未对齐设备像素的线会有模糊边缘。
- 滚动、sheet、转场移动已绘制的行而不重绘它，可能把线留在像素之间；静止后 `_PixelRealignment` 让漂移的行重绘一次。

### 13.2 行尾

| 行为 | 行尾 |
|---|---|
| 打开页面 | `AppGlyphs.chevronForward`（18 dp） |
| 打开对话框 | 无箭头 |
| 选项行 | 当前值 + `AppGlyphs.picker` |
| 复选框 | `CommonCheckBox` |
| 开关 | Material `Switch` |
| 拖动排序 | `SortHandle` |

`ListItem` 变体：`ListItem()` / `.open()` / `.next()` / `.options()` / `.input()` / `.checkbox()`。

### 13.3 设置行组件

`lib/widgets/config_item.dart` 提供共享词汇：

| 组件 | 用途 |
|---|---|
| `ConfigToggleItem` | 开关 |
| `ConfigOptionsItem` | 选项（iOS 弹出式行） |
| `ConfigTextItem` | 文本输入 |
| `ConfigListEditItem` | 列表编辑 |

每个接受 `selector`（`ProviderListenable`，通常 `someProvider.select(...)`）与 `onChanged(ref, value)`，并**监视自己的 selector**，所以改一个设置只重建一行而不是整个区块。

标题与副标题是 `ConfigLabel` 回调，接收 `AppLocalizations`，让 `IPv6` 这样的字面标签与本地化标签保持同一形状。

### 13.4 区块结构

`generateSection` / `generateAnimatedSection`：

```
ListHeader（标题 + 至多几个动作）
  ↓ 6 dp
[ 成组行 ]
  ↓
ListFooter（脚注）
```

- 行的**副标题显示其值或状态，永不显示该设置做什么**（如 iOS 设置）。解释放在区块的 `footer`，由 `ListFooter` 绘制，一条设置一句，点名它。
- 区块头承载标题与至多几个组上动作，`ListHeader` 把它们画成 **36 dp** tonal 按钮（`TonalButtonSize.section`），前两个成组、在标题上居中。
- 每个表头都与带按钮的表头等高，距下方组 **6 dp**，所以有没有动作都一致。
- 有选中项的组把动作换成删除与全选，**永不**用文字按钮。
- sheet 的组放在页面用的 16 dp 内缩里。

### 13.5 行动作与排序模式

**列表行或卡片不带尾部"更多"按钮。** 次要动作进 `ContextMenuRegion`，由以下方式打开：

- 右键
- 触摸长按
- 聚焦行的菜单键或 `Shift+F10`
- Android TV 上 `RemoteFocusAdapter` 启用时，按住 select 键超过长按超时（短按 select 仍按压该行，在键抬起时而非按下时）

点击保留行的主操作。

一个长按在行上只能表示一件事：

| 场景 | 长按行为 |
|---|---|
| 行没有上下文菜单（纯列表编辑器、全局/配置规则、按需 SSID、自定义代理） | 拖动该行，**没有排序按钮** |
| 行有上下文菜单 | 只在排序模式下重排 |

排序模式中，列表是页面时由 `CommonScaffold` 拥有该模式（页面向它传 `canSort`）。改动即时生效，所以什么都不确认：模式开启时，栏只显示它的开关（已选中），返回或关闭按钮原地淡出而不是提供第二条同样的出路（如 iOS 编辑时隐藏返回按钮）；系统返回仍结束该模式。

`SortableTrailing` 在行尾**弹入**一个 `SortHandle`，同时它替换的尾部收缩消失。动效是**绘制出来的，不是构建出来的**，行只在两个宽度不同时重排，因为屏幕上每一行都同时动画。

### 13.6 行分隔线的例外

记录列表、请求、连接、日志、DNS 查询**不分组成跑道**：它们跑到数千行高，一张那么长的卡片读作一块板而不是一组。

一个三四行的堆叠行本身已经是一块，所以每行是自己的一张卡片 `RecordListItem`：`xl` 圆角、距边缘 16 dp、距下一张 8 dp，如个人资料卡。每张卡取同样的填充，所以标签上的警告与错误色调仍然突出。

排成表格时（一行是横跨多列的一行字），它们去掉间隙，每隔一行给抬升填充，内缩并圆角如列表头，让眼睛保持行。

---

## 14. 面板：Sheet / Dialog / Popup

### 14.1 四种面板

| 类型 | 尺寸 | 圆角 | 动效 |
|---|---|---|---|
| 底部 Sheet | detents `[9/16, 0.9]` | 顶部 `xxl` | UIKit 临界阻尼弹簧（~500 ms） |
| 侧边 Sheet | 宽 `clamp(w × 0.4, 360, 560)`，高满窗 | `none` | 进 300 ms / 出 200 ms，`standardDecelerate` |
| 表单 Sheet | 440 宽、720 高上限、40 边距 | 卡片形 | 与警告框同弹簧 |
| 警告框 | 宽 **348**，内容上限 **300** | **34** | 404 ms，1.2× 缩入 |
| 弹出菜单 | 宽 160–280 | 条目 `md`(16)，卡片 `md + 8 = 24` | 750 ms 开 / 300 ms 关 |

### 14.2 底部 Sheet 细节

```dart
const snapSheetDetents = [9 / 16, 0.9];
```

拖拽把手（`lib/ui/sheet_header.dart`）：

```dart
static const _topPadding = 6.0;
static const _thickness = 4.0;
static const _width = 28.0;
```

- 把手高 4 dp、宽 28 dp、顶部留 6 dp，`onSurfaceVariant`，圆角 `thickness/2`。
- `SheetToolBar` = 把手（或其占位）+ 应用栏 + 补足到 `sheetAppBarHeight = 68` 的尾距。
- 浮动头部用 **8 段** `easeInOut` 渐变的 **90%** 不透明度遮罩，并向脚部溢出 `overhang` 以免露出边缘。
- 键盘把 sheet 的脚抬起，见 §8.6。

### 14.3 警告框细节

`lib/ui/dialog.dart`：

```dart
const double _actionGap = 8;
const double _actionInset = 16;
const double _actionHeight = 48;
const double _textInset = 30;
const double _alertWidth = 348;
const double _contentMaxWidth = 300;
const double _fieldInputGap = 4;
const double _pressGrowth = 4;
const double _pressPull = 3;
const double _pressedFillShare = 0.4;
const double _pressedDarkDefaultShare = 0.5;
```

对齐 UIKit 的测量（在同一页面上对比）：

| 元素 | 位置 |
|---|---|
| 标题 | 下移 21 dp |
| 消息 | 标题下 6 dp |
| 输入框 | 消息下 16 dp |
| 按钮 | 二者之下 20 dp |

- 普通按钮标签取 medium 字重。
- 浅色消息取 `onSurfaceVariant` @ 85%（如 iOS 的 vibrant secondary label）。
- 无消息的标题在正文权重中居中，所以有输入框的警告框（`hasFields`）也居中，两侧各留出尾部控件的宽度以保持标题在中心。
- 输入框与按钮同宽、同填充，文字与标题对齐，标签作为 placeholder。

### 14.4 弹出菜单细节

```dart
const _screenMargin = 16.0;
const _anchorOverlap = 8.0;
const _avoidGap = 8.0;
const _cardInset = 8.0;
const _itemRadius = AppCorner.md;         // 16
const _cardRadius = _itemRadius + _cardInset;  // 24
const _itemIconSize = 20.0;
const _submenuArrowSize = 16.0;
const _itemPadding = EdgeInsets.symmetric(horizontal: 12, vertical: 12);
const _dividerHeight = _cardInset * 2 + 1;   // 17
const _menuMinWidth = 160.0;
const _menuMaxWidth = 280.0;
const _defaultMaxVisibleItems = 7;
const _dwellDuration = Duration(milliseconds: 400);
```

**阴影**（柔和而宽，HyperOS 风格而非 Material 高度）：

```dart
// 浅色 strength = 1，深色 = 3
BoxShadow(alpha: 0.05 × strength, blur: 10, offset: (0, 2))
BoxShadow(alpha: 0.04 × strength, blur: 30, offset: (0, 4))
```

**条目**：

- 图标 20 dp，子菜单箭头 16 dp。
- 内边距 12 dp 四周（有箭头时右 8）。
- 分割线高 17 dp（两侧卡片内缩 + 1）。

**可见行数上限**：`maxVisibleItems` 设为 7 时，第 8 行露出一半，切口告诉菜单可以滚动；此时只构建视野内的行，所以需要所有行等高。

**值选择行**：`ListItem.options` / `ConfigOptionsItem` 的条目**只有文字**（如 iOS 选择器），字形属于动作菜单。从菜单选中且菜单会返回其控件时，选择在弹窗**落定后**才执行，所以会重建应用的设置（如语言）永不在返回的托盘下执行。控件用 `PopupValueText` 显示值，它在新值上模糊过渡，同时控件缓到新宽度。

**从某点打开的菜单**（上下文菜单、编辑器的）立即执行选择。

---

## 15. 表单与输入

### 15.1 表单形态

编辑多个值的表单用 `showFormSheet`：

| 平台 | 形态 |
|---|---|
| 手机布局 | 底部 sheet |
| 其他 | 卡片 `FormSheetRoute` 居中于导航器之上（如 iPad 与 macOS 呈现表单 sheet，像警告框一样移动） |

两者都给页面 `SheetType.bottomSheet` 或 `SheetType.formSheet`，共享 `CommonScaffold` 的 sheet 栏，所以一个页面服务两种形态。

### 15.2 字段样式

**表单字段是裸露的**：

- `FormRowTextField` 放在 `FormRow` 末尾，标题即标签。
- **无描边无填充**，所以聚焦字段的唯一轮廓是它所在行的焦点环。
- 文本右对齐（`TextAlign.end`）。
- 光标宽 2 dp。

`RenderEditable` 会在文本之后保留光标的余量，所以字段把提示结束得同样短、同样深地伸进行的内边距：光标跟随提示或文本，文本结束在别的行的值结束的位置。

```dart
static const _cursorWidth = 2.0;
static const _caretMargin = 1 + _cursorWidth;  // 3
```

**浮动标签**：所有无标签字段都设 `floatingLabelBehavior: FloatingLabelBehavior.never`——`InputDecorator` 会对空字段的浮动标签做动画，即便根本没有标签（`InputDecoration.collapsed` 也一样），每一帧都会布局 decorator，在 sheet 内则是整个表单。

### 15.3 各场景的字段填充

| 场景 | 样式 |
|---|---|
| 表单字段 | 裸，只在聚焦时由行画焦点环 |
| 填充控件中的字段 | 取它们的填充，比所在表面高一级（`FilledInputTheme`） |
| 警告框内的字段 | 与按钮同宽、同填充，文字与标题对齐，标签作 placeholder |
| 填充文本块下的字段 | 取块的宽度与圆角 |
| 自定义颜色的十六进制字段 | 卡片只有填充，所以它也填充 |
| 搜索框 | 填充胶囊 |
| sheet 内停靠的搜索框 | 像 Dock 一样浮动，用浮动填充 + Dock 阴影，或 Dock 的玻璃 |
| 编辑器的查找栏 | 取按钮的填充，比栏高一级 |
| **页面上孤立的字段** | **描边**，浮动标签切入描边（`AppShape.input`） |

**孤立的字段永不填充**：这里填充意味着结构，一个填充的字段会读成又一张卡片或又一行；浅色模式下悬停覆盖还会把它拉到页面本身的色调。

**没有字段计数它的字符**（iOS 与 macOS 都不），长度限制通过 formatter 实现。

Material 把填充字段的整个轮廓描成 `onSurfaceVariant`，会让胶囊在深色 sheet 里比任何东西都亮——所以填充字段只用 `none` 边框。

### 15.4 表单结构

```dart
FormRow(
  title: '…',
  leading: …,       // 可选图标
  trailing: …,      // 可选控件
  invalid: false,   // 标红该行
  child: FormRowTextField(...),
)
```

`FormRow` 常量：

```dart
static const _spacing = 16.0;
static const _titleMaxLines = 2;
static const _titleMaxWidthFactor = 0.5;
```

- 有 `trailing` 时标题最多占 (宽 − 间距) × 0.5。
- 行不能承载错误行，所以表单拒绝的保存把该行标为 `invalid`，并在组上方 `ErrorBanner` 说明原因，此后每次编辑都跟随。

**保存**是脚手架的 `confirmationAction`：

| 形态 | 位置 |
|---|---|
| 底部 sheet | 栏内最后 |
| 表单 sheet | 第一页脚部的警告框按钮行中，填充的默认按钮，旁边是取消（取代关闭的位置），如 SwiftUI 在 macOS 上放置确认动作 |

其他动作（如快速编辑）留在栏内。

**单值提示**（URL 导入、重命名、密码）保持为警告框（`CommonDialog`、`InputDialog`），如每个 Apple 平台。键值对（`MapEditView` 添加的）是两个值，是一个表单。

### 15.5 其他输入控件

| 控件 | 规格 |
|---|---|
| `CommonCheckBox` | 支持圆形（`AppShape.circle`） |
| `Slider` | 轨道高 16、拇指 4 × 44、`GappedSliderTrackShape`、`trackGap 6`、激活 `primary`、非激活 `secondaryContainer`、刻度半径 2 |
| `DashboardSwitch` | 52 × 32，拇指 24，无轮廓，关闭态是实心轨道 |
| `SegmentedControl` | 高 44、内缩 4、胶囊底、选中块 `primary` |
| `CommonChip` | 内边距 8/6/3/3、`caption1` 强调、圆角 `sm` |
| `TonalChip` | 内边距 6/1、`_CapCentered` 按 0.7 cap-height 居中 |
| `SearchField` | 填充胶囊、20 dp 搜索图标、20 dp 清除图标 |

### 15.6 文本字段建议

Android 上 Flutter 把 `enableSuggestions: false` 映射为可见密码输入类型，而不只是"无建议"。输入法会因此把字段当作密码：无剪贴板面板、无中文布局，某些系统还会用安全键盘。应用剪贴板权限被拒时，就完全没有办法粘贴链接了。

所以**只有**秘密字段（`SecretTextFormField`、`FormRowSecretTextField`）请求密码键盘；不该被纠正的链接、键、规则、正则字段只设 `autocorrect: false`。`test/lint/text_field_suggestions_test.dart` 在 `lib/` 其他地方禁止 `enableSuggestions: false` 与 `TextInputType.visiblePassword`。

---

## 16. 滚动

### 16.1 滚动条

`lib/ui/scroll.dart`：

| 常量 | 值 |
|---|---|
| `_restThickness` | 6 |
| `_activeThickness` | 10 |
| `_crossAxisMargin` | 2 |
| `_minThumbLength` | 48 |
| `_bottomInsetGap` | 8 |
| 淡出 | 250 ms |
| 消失前停留 | 1200 ms |
| 强调动画 | 120 ms，`easeOut` |
| 形状 | `AppShape.full` |

**侧向刮擦减速**：指针每偏离抓取位置一个 `_scrubBandWidth = 48` dp，内容每像素的移动量减半：

```dart
const _scrubRates = [1.0, 0.5, 0.25, 0.125];
```

抓取时显示一个速率提示胶囊（`_ScrollbarHintPill`）。释放后提示停留 500 ms 才淡出，避免松手再抓时闪烁。

### 16.2 显示规则

`BaseScrollBehavior`（`lib/ui/scroll_behavior.dart`）：

- 桌面平台（Windows / macOS / Linux）显示滚动条，触屏平台隐藏。
- 鼠标也作为拖动设备（桌面）。
- 横向滚动永不显示滚动条。
- `_OwnBarScrollBehavior` 让滚动条不叠加在它驱动的滚动视图上，所以嵌套列表保留自己的。

### 16.3 物理

`NextClampingScrollPhysics`：

```dart
@override
double carriedMomentum(double existingVelocity) => 0;
```

**理由**：iOS 的动量是为它自己的摩擦甩动设计的，钳制物理会把它乘上去。

越界回弹用 `ScrollSpringSimulation`。

`FollowEndScrollPhysics`：跟随模式且原本在末尾时，尺寸变化后保持贴底（日志、连接等实时列表）。

### 16.4 其他

- `ScrollToEndBox`：自动滚到末尾的容器。
- `FloatingScrollbar`：带速率提示的浮动滚动条。
- 仪表盘用 `DashboardFadeOut`（44 dp 渐变）代替滚动条：列表跑过卡片时最后 44 dp 淡出。

---

## 17. 仪表盘

### 17.1 密度与断点

`lib/views/dashboard/layout.dart`：

```dart
const _minDesktopWidth = 640.0;
const _minFourColumnWidth = 860.0;
const _minStandardFourColumnWidth = 1040.0;
const _minStandardHeight = 824.0;
const _mobileMaxWidth = 560.0;
```

```dart
int dashboardColumnsOf(double width) => width < 860 ? 2 : 4;

enum DashboardDensity { standard, compact, mobile }
```

| 密度 | 条件 | 效果 |
|---|---|---|
| `mobile` | 宽 < 640 | 单列，可编辑 |
| `compact` | 4 列但宽 < 1040，或高 < 824 | 字号、启动按钮、瓷砖缩小，去掉副标题 |
| `standard` | 其余 | 完整 |

```dart
double dashboardMobileSideOf(double width) => max(16, (width - 560) / 2);
```

### 17.2 栅格与高度

| 常量 | standard | compact |
|---|---|---|
| 顶部行高 | 208 | 176 |
| 控制行高 | 64 | 56 |
| 中部行高 | 176 | 152 |
| 摘要行高 | 120 | 112 |
| 列宽上限 | 440 | 440 |

- 内边距：起 16、上 = 安全区、末 24、下 24 + 安全区。
- 列宽 = `min(room / columns, 440 × scale)`，剩余空间均分为左右内缩。
- 内存卡需要列宽 ≥ **290 dp**；不够时服务状态卡占 2 列，内存卡不显示。
- 表格放得下（`constraints.maxHeight ≥ rowsHeight + tablesHeight`）时，连接表（flex 3）与代理组（flex 2）填充剩余高度；否则用摘要卡。

### 17.3 卡片

```dart
// 圆角（刻意超出 AppCorner 刻度：仪表盘卡比应用内任何面都高）
abstract final class DashboardCorner {
  static const desktopCard = 32.0;
  static const mobileCard = 34.0;
  static const block = 20.0;
  static const mobileBlock = 22.0;
  static const tab = 16.0;
  static const tableHeader = 12.0;
  static const badge = 14.0;
}
```

内边距：

| 密度 | 水平 | 垂直 |
|---|---|---|
| standard | 22 | 20 |
| compact | 20 | 16 |
| mobile | 20 | 18 |

`DashboardCard`：

- 填充 `DashboardColors.card`，形状 `AppShape.all(32 或 34)`。
- **投影用 `e1`**（见下）。
- `clipBehavior` 默认 `Clip.none`，让在 `ElasticPress` 下膨胀的控件能画出卡片边缘；只有内容本身出血的卡片才裁剪。

### 17.4 颜色映射

`DashboardColors`（`lib/views/dashboard/style.dart`）：

| 角色 | 映射 |
|---|---|
| `page` | `SurfaceLevel.page` |
| `card` | `SurfaceLevel.card` |
| `tone` | `SurfaceLevel.block` |
| `tone2` | `SurfaceLevel.inset` |
| `track` | `surfaceContainerHighest` |
| `thumb` | `outline` |
| `series2` | `primary` 45% 叠在 `card` 上 |
| `ok` / `okContainer` / `onOkContainer` | `success` / `successContainer` / `onSuccessContainer` |
| `warn` / `warnContainer` / `onWarnContainer` | `warning` / `warningContainer` / `onWarningContainer` |

### 17.5 阴影

`DashboardShadows`——**值是设计稿的 CSS 阴影**。CSS blur 是其 sigma 的两倍，而 Flutter 把 blur 半径 r 变成 sigma `0.577r + 0.5`，所以每个都经过 `_blur`，否则渲染重三分之一。

```dart
static double _blur(double cssBlur) =>
    cssBlur <= 1 ? 0 : (cssBlur / 2 - 0.5) / 0.57735;
```

| 档 | 浅色 | 深色 |
|---|---|---|
| `e1` | `α0D y0.5 b1` + `α0A y2 b6` + `α17 y12 b32 s−6` | `α80 y1 b2` + `α8C y10 b28 s−6` |
| `e2` | `α0F y1 b3` + `α14 y10 b24` + `α38 y32 b64 s−12` | `α80 y2 b6` + `αB3 y32 b64 s−12` |

### 17.6 文本角色

`DashboardText`：

| 角色 | 样式 |
|---|---|
| `cardTitle` | `footnote` 强调，`onSurfaceVariant` |
| `mobileCardTitle` | `subheadline` 强调 |
| `label` | `caption1` + 等宽数字，`onSurfaceVariant` |
| `figure` | 任意 `AppTextStyle` 强调 + 等宽数字 + `letterSpacing: -0.02 × size` |
| `unit` | `subheadline` 强调，`onSurfaceVariant` |
| `rowTitle` | `subheadline` 强调 |

**每个数值都用等宽数字**，所以数字变化时不会抖动。

数字格式：

```dart
String dashboardCount(int count) =>
    count < 100000 ? NumberFormat.decimalPattern('en').format(count) : count.compact;
```

分组数字，直到超过手机宽度的三分之一。

### 17.7 卡片清单

| 卡片 | 字形 | 说明 |
|---|---|---|
| `CurrentOutboundCard` | `status` | 出口 IP + 启动按钮（**不可移除**，它承载启动按钮） |
| `NetworkSpeedCard` | `speed` | 上传/下载双图 |
| `OutboundModeCard` | `split` | 出站模式分段 |
| `VpnCard` | `vpn` | VPN 开关 |
| `NetworkTakeoverCard` | `shuffle` | 系统代理 / TUN 开关 |
| `ServiceStatusCard` | `verified` | 服务可达性网格 |
| `ProfilesCard` | `profiles` | 当前配置 |
| `ProxyGroupsCard` | `layers` | 代理组摘要 |
| `ConnectionStatsCard` | `connections` | 连接统计 |
| `IntranetIpCard` | `devices` | 内网 IP |
| `MemoryInfo` | `memory` | 内存占用 |

### 17.8 出站卡（旗舰卡）

`_OutboundMetrics` 三档：

| 项目 | standard | compact | mobile |
|---|---|---|---|
| `gap` | 16 | 16 | 10 |
| `ipTop` | 10 | 8 | 12 |
| `cellGap` | 28 | 22 | 24 |
| `badgeSize` | 48 | 40 | 48 |
| IP 样式 | `title1` | `title2` | `title2` |
| `buttonSize` | 104 | 84 | 88 |
| `buttonSlot` | 120 | 100 | 88 |
| `decorationSize` | 290 | 250 | 240 |
| `decorationEnd` | −64 | −64 | −60 |
| `decorationTop` | −87 | −75 | −66 |

**装饰形状**：右上角一个 `MaterialShapes.cookie12Sided`，随启动状态变形：

```dart
const _decorationGrowth = 0.12;                    // 放大 12%
const _decorationTurn = 2 * math.pi / 12;          // 转 30°
const _morphDuration = Duration(milliseconds: 450); // easeOutBack
```

运行时它从 `colors.tone` 过渡到 `primary` 10% 叠在卡片色上，同时旋转并放大。

### 17.9 通用仪表盘组件

| 组件 | 规格 |
|---|---|
| `DashboardCardHeader` | 高 24（mobile 26），字形 18，间距 8 |
| `DashboardFitText` | 最小缩到 0.75，再小则省略号 + Tooltip |
| `DashboardCappedRow` | 尾部先按自身宽度布局，最多占行的 50%，超出则缩小 |
| `DashboardConnectedGroup` | 间距 3，最后按下的那个画在邻居之上 |
| `DashboardConnectedButton` | 240 ms `easeOutCubic`，两端圆化整组、内侧角紧凑、选中变胶囊 |
| `DashboardChip` | 高 24（大号 28），内边距 10（大号 12），圆点 6 dp |
| `DashboardBlock` | 卡内一级色块 |
| `ShapeBadge` | M3 Expressive 形状 + 居中的子级，可旋转、可描边 |
| `DashboardSwitch` | 52 × 32，拇指 24，无轮廓 |
| `DashboardSwitchRow` | 开启时 cookie-6 图标容器（`primary` 填充 + `onPrimary` 字形），关闭时普通圆（卡片色 + `onSurfaceVariant` 字形），`AnimatedSwitcher` 300 ms 切换 |
| `DashboardFadeOut` | 最后 44 dp 渐变淡出 |
| `DashboardIconButton` | 32 × 32，字形 18 |

### 17.10 连接表

```dart
const _maxFeedRows = 100;
const _midTableWidth = 620.0;
const _wideTableWidth = 880.0;
const _cellInset = 8.0;
const _tableGap = 12.0;
const _headerHeight = 32.0;
const _minRows = 3;
```

- 表头 `caption1`，圆角 `tableHeader = 12`。
- 每隔一行给抬升填充，内缩并圆角如表头。
- 轮询 1 秒（跟踪连接时）或 2 秒。
- 渲染节流：`renderThrottleDuration` 后才把最新快照放上屏，避免高频刷新打断滚动。

### 17.11 速度卡

```dart
const _minSpeedScale = 8 * 1024.0;   // 最小量程，让空闲噪声贴近基线
const _chartBleed = 3.0;             // 图表出血
static const _aspectRatio = 1.06;
static const _padding = EdgeInsets.fromLTRB(16, 14, 16, 0);
static const _titleGap = 4.0;
static const _chartGap = 12.0;
static const _bleed = 2.0;
static const _minChartHeight = 36.0;
```

数值用 `display`（40 dp）或 `largeTitle`（34 dp），单位 `subheadline`。

### 17.12 折线图（`lib/ui/line_chart.dart`）

- 高斯模糊预处理：`_sigma = 1.5`，半径 `ceil(1.5 × 2.5) = 4`。
- Catmull-Rom 斜率，钳制使曲线不跌破 0。
- 描边宽 **2.0**，`StrokeCap.round`，`StrokeJoin.round`。
- 面积填充 alpha **0.14**。
- 增量更新：`revision` 每追加一个样本 +1，其他变化（清空）重开。
- 用 `RepaintBoundary` 隔离重绘。

### 17.13 服务状态卡

- 显示 **4** 个服务（`_shownServiceCount = 4`）。
- 2 列网格（`_columns = 2`），单元内缩 7 dp。
- 单元格圆角 `DashboardCorner.tab - 7 = 9`，外层 `16`。
- 状态文字 `caption1`，标题 `footnote` 强调。
- 状态标签用 `AppShape.full` 胶囊 + `caption1`。
- 服务图标 40 dp 徽章，字形占 56%。
- 检查全部按钮带加载态。

### 17.14 代理组摘要卡

```dart
const _stackedIcons = 4;    // 堆叠图标数
const _blockInset = 10.0;
static const _inset = 0.15; // 图标内缩
static const _maxWidth = 260.0;  // 空态最大宽
```

延迟文字 `caption1`，组名 `subheadline` 强调。

### 17.15 编辑模式

- 桌面密度下不可编辑（`editable = !density.isDesktop`）。
- 编辑时栏标题变"编辑仪表盘"，返回动作退出，确认动作是勾。
- 卡片用 `GlobalKey` 保持状态，支持拖动排序。
- 编辑时 `scrollCacheExtent` 扩到所有卡片数，因为一张视口里要布局全部。

### 17.16 状态按钮（桌面）

`CoreStatusButton` 只在桌面、非编辑模式、且 `coreLib == null` 时显示：

```dart
static const _holdDuration = Duration(milliseconds: 600);
```

- 保持独立的**仅显示**状态，让快速的 `connecting → connected` 仍显示至少 **600 ms** 的进度而不是闪一下。
- 保持只在观察到向 `connecting` 的转换后才武装；挂载时已在连接中不会凭空造出新延迟。
- 真正的 `disconnected` 转换立即取消保持，所以失败永不被隐藏。
- 显示保持期间或真正连接中的点击是惰性的。
- 连接/断开状态的点击显示相应确认并把重启委托给 `CoreAction`，widget 永不直接启动 Core。

---

## 18. 代理页

### 18.1 卡片类型

```dart
enum ProxyCardType { loose, standard, compact }
enum ProxiesType { tab, list }
enum ProxiesLayout { loose, standard, tight }
enum ProxiesSortType { none, delay, name }
enum ProxiesIconStyle { filled, plain, hidden }
```

### 18.2 卡片尺寸

`lib/views/proxies/common.dart`：

```dart
const proxyGridSpacing = 8.0;

double getItemHeight(ProxyCardType type) {
  final base = 16 + subheadlineHeight * 2 + footnoteHeight + 8 + 4;
  return switch (type) {
    loose   => base + caption2Height + 6,
    standard => base,
    compact => base - subheadlineHeight,
  };
}
```

高度**由文本高度推导**，所以随字体与缩放自适应。

### 18.3 卡片外观

- 圆角 `AppCorner.lg`（20），水平内边距 12。
- 名称 `subheadline`（`compact` 单行，其余两行）。
- 类型 `footnote`。
- 选中：`SelectionBadge`（22 dp）在名称右侧；首选：`SelectionBadge.preferred(inUse:)`。
- 名称与详情间距 8。
- 延迟文字对齐：`loose` 左，其余右。
- `loose` 额外显示一行 `caption2`。

### 18.4 网格

```dart
SliverGridDelegateWithFixedCrossAxisCount(
  crossAxisCount: columns,
  mainAxisSpacing: 8,
)
```

列数来自 `getProxiesColumns(width, layout)`。

### 18.5 组头与列表

| 常量 | 值 |
|---|---|
| `_pinnedHeaderGap` | 8 |
| `_headerInset` | 10 |
| `_headerButtonInset` | 4 |
| `_groupInset` | 16 |
| `_liftDistance` | 16 |
| `_groupMinWidth` | 360 |
| `_groupMaxColumns` | 3 |

- 组卡圆角 `xl`，组图标圆角 `xl − 10`。
- 组名 `callout` 强调。
- 选中代理名 `caption1` 强调 + `toLight`。
- 滚动到选中项用 **300 ms** `easeOutCubic`。
- 吸顶表头在滚动时抬起 16 dp。

### 18.6 组标签

代理页的组标签是紧凑选择 chip，当前组用选中分段的实心 `primary`。

一旦溢出，末尾的 tonal 按钮把全部组作为弹出菜单打开，当前项打勾并滚入视野。一个配置可能有几十个组，所以菜单停在 **7 行 + 下一行的一半**。

### 18.7 延迟测试的视觉反馈

- 一个键在被 `TaskPool` 真正运行前保持 `queued`，所以节点卡最多为 `maxConcurrentDelayTests` 个节点动画加载器，其余显示静态字形——每个排队节点都放加载器会让快速滚动掉帧。
- 组的延迟测试按钮（标签页的动作与列表页的组头一样）在 `delayTestingGroupsProvider` 持有该组时旋转。
- 失败在 `proxied` 变为 true 后仍显示为"检查中"**5 秒**（`ProbeStartHold`）。

---

## 19. 配置页

### 19.1 结构

配置页全部由 `generateSection` 构建的分组组成，行来自 `ConfigToggleItem` 家族。

- `lib/views/config/` 有 16 个配置文件，约 5.7k 行。
- 页面用 `ListItem.open` / `.next` / `.options` / `.input` 组合。
- 行副标题显示值，解释进 `footer`。

### 19.2 自定义配置总览

保留规则、提供者、DNS 三行（每个自定义配置都跑它们），把拨号代理、NTP、嗅探、TUN 放在单个"更多"行后——打开一个列出它们及其状态的页面。

两个列表都是同样的分组行：字形、标题、状态，以箭头结束。另一个很少改动的区块加入"更多"列表而不是占一行。

### 19.3 快速编辑

快速编辑（打开页面所编辑文本的撰写按钮）**只出现在文本就是全部内容的地方**：

| 允许 | 不允许 |
|---|---|
| 整个列表或区块（自定义配置的规则、代理的分享链接、DNS/NTP/嗅探区块） | 只编辑其他设置也会写的东西的一部分 |
| 一个孤立的项（表单编辑的代理组或代理的定义） | 应用级 DNS 页（它是叠在每个 URL/文件配置的 DNS 上的几个键） |

后者的文档会显示一个片段并拒绝它遗漏的内容。

### 19.4 主题设置页（`lib/views/theme.dart`）

| 控件 | 规格 |
|---|---|
| 色板 | 44 dp 圆，内点 32 dp，选中环 2.5 dp，选中勾 18 dp，200 ms `easeOutBack` 缩放进入 |
| 吸管徽章 | 20 dp，右下角，间距 2 |
| 迷你屏预览 | 高 30 dp，宽 30 × 96/66，`ClipRSuperellipse(AppRadius.sm)` |
| 实时预览 | 手机宽 168 dp，300 ms 过渡 |

色板支持右键上下文菜单（`ContextMenuRegion`），可选跟随系统强调色。

### 19.5 主题预览

`ThemeLivePreview` 渲染首页如主题设置将要绘制的那样；它的目的地可点击以试标签动画。

`MiniScreenThumb` 把 96 × 128 的屏幕缩到 30 dp 高，底部对齐，用 `ClipRSuperellipse(AppRadius.sm)` 裁剪。

---

## 20. 加载与空状态

### 20.1 环形加载

`CommonCircleLoading`：

```dart
static const _turnDuration = Duration(milliseconds: 1000);
static const _liveArea = 20 / 24;       // 字形保持其轮廓在 20/24 内
static const _minStrokeWidth = 1.5;
static const _tail = 0.72;              // 尾迹占圆周的 72%
```

- 描边宽 `max(side / 12, 1.5)`。
- 底色轨道 `primary` @ 12%。
- 彗星弧用 `SweepGradient`（`opacity0 → opacity30 → color → opacity0`，stops `[0, 0.36, 0.72, 0.72]`）。
- 弧尾端画一个实心圆点。
- `RepaintBoundary` 隔离重绘。
- `CommonCircleLoading.page` 是 48 dp。

### 20.2 骨架

`SkeletonPill`：

```dart
static const _period = Duration(milliseconds: 1600);
// 透明度 0.75 + 0.25 × cos(2πt)
```

- 从墙钟起始，所以不同时刻出现的条同步脉动。
- 填充 `surfaceContainerHighest`，形状 `AppShape.full`。
- `disableAnimations` 时停止并画静态条。
- `SkeletonText` 把 `SkeletonPill` 叠在一个空格字符上，尺寸跟随字号与文本缩放。

**只用在一个已在屏幕上的卡片内的单个值**，永不整屏。

### 20.3 空状态

`NullStatus`（`lib/ui/null_status.dart`）：

```dart
const _compactHeight = 560.0;
const _staggerStep = Duration(milliseconds: 60);
const _enterRise = 12.0;
// 插图入场 900 ms
```

- 垂直位置 `Alignment(0, -0.2)`，水平内边距 32 / 24，内容上限 **320** 宽。
- 插图 **160** dp（高度 < 560 时 **120** dp）。
- 标题 `headline` + `onSurface`，说明 `subheadline` + `onSurfaceVariant`，间距 8。
- 动作间距 24。
- 每项交错 **60 ms** 入场。

### 20.4 空状态插图

**不用图片资源**。`EmptyIllustration` 把一个字形填在 M3 Expressive 多边形上：

| 含义 | 形状 | 字形 |
|---|---|---|
| 数据 | `cookie12Sided` | `resources` |
| 日志 | `cookie9Sided` | `logs` |
| 代理 | `sunny` | `proxies` |
| 配置 | `cookie7Sided` | `profiles` |
| 脚本 | `cookie6Sided` | `code` |
| 规则 | `clover8Leaf` | `rules` |
| 连接 | `cookie4Sided` | `connections` |
| 请求 | `softBurst` | `requests` |
| DNS | `puffy` | `dns` |
| NTP | `pentagon` | `clock` |
| 嗅探 | `gem` | `eye` |
| TUN | `clamShell` | `vpn` |
| WiFi | `cookie7Sided` | `wifi` |
| 应用 | `clover4Leaf` | `appsList` |
| 图标 | `verySunny` | `iconTile` |
| 字体 | `puffyDiamond` | `font` |
| 权限 | `sunny` | `lock` |
| 相机 | `cookie6Sided` | `camera` |
| 错误 | `softBurst` | `error` |
| 搜索 | `flower` | `search` |

配色：

| 角色 | 常规 | 错误 |
|---|---|---|
| `shape` | `primaryContainer` | `errorContainer` |
| `ink` | `onPrimaryContainer` | `onErrorContainer` |
| `highlight` | `primary` | `error` |
| `accent` | `tertiary` | `error` |
| `accentContainer` | `tertiaryContainer` | `errorContainer` |

字形取 `onPrimaryContainer` 而非 `primary`：`content` 变体在深色下可能让 `primaryContainer` 和 `primary` 一样浅。

**三种入场编排，随机选择**：

| 编排 | 描述 |
|---|---|
| `spin` | 形状旋转进入 |
| `morph` | 从圆变形而出 |
| `orbit` | 弹入，同时装饰点环绕到位 |

几何（相对边长的比例）：

```dart
static const _shapeExtent = 0.72;
static const _haloExtent = 0.84;
static const _glyphExtent = 0.4;
static const _accents = [
  (_Accent.blob, Offset(-0.32, 0.3), 0.14),
  (_Accent.dot,  Offset(-0.4, -0.18), 0.05),
  (_Accent.ring, Offset(0.42, 0.15), 0.075),
];
```

**每种编排必须落到同一个最终帧**，因为跳过入场时直接绘制该帧。

### 20.5 切换器

`NullStatusSwitcher`：

```dart
static const _exitDuration = Duration(milliseconds: 150);
static const _loadingStatusDelay = Duration(milliseconds: 400);
```

- 只有**首次**加载被扣住；后续加载保留屏幕上已有的内容。
- 首次加载超过 400 ms（约一次路由转场）才显示 `loadingStatus` 插画（无标签）；更早结束的直接进内容，所以插画永不闪。
- 切换：300 ms 进 / 150 ms 出，`emphasizedDecelerate` / `easeIn`，缩放 0.92 → 1.0。

---

## 21. 可访问性与平台适配

### 21.1 焦点与遥控

`lib/ui/focus.dart`：

| 组件 | 作用 |
|---|---|
| `PageFocusScope` | 每页独立的焦点作用域，边界行为交给父作用域 |
| `RemoteFocusAdapter` | TV / 遥控适配：方向键遍历、焦点落点、浮动键盘 |
| `FocusMovedByKey` | 记录焦点是否由键盘移动（决定是否画焦点环） |
| `_RemoteTraversalPolicy` | 遥控遍历策略 |
| `RemoteFocusAdapter.isEnabledOf(context)` | 查询是否启用 |

**遥控选择键**：`select`、`enter`、`numpadEnter`、`gameButtonA`。

**焦点环**（`lib/ui/surface.dart`）：

```dart
const _focusRingWidth = 2.0;
const _focusRingGap = 1.0;
```

- 2 dp primary 描边，TV 导航依赖它——纯填充变化从沙发上跟不上。
- **只在键盘移动了焦点**（`FocusMovedByKey`）或遥控驱动应用时给卡片/成组行画环：点击把焦点放在读者已经在看的地方，字段的光标已经标记它，此时环会读成行周围的选中框。
- `standsOff` 让环离开边缘 1 dp（`strokeAlign = 1 + 2×gap/width`），用于 primary 填充或按钮自己的玻璃会盖住它时。

### 21.2 减少动态

见 §8.8。

### 21.3 系统 UI（Android）

```dart
SystemUiOverlayStyle(
  statusBarColor: Colors.transparent,
  statusBarIconBrightness: iconBrightness,   // 浅色主题 → 深图标
  systemNavigationBarIconBrightness: iconBrightness,
  systemNavigationBarColor: Colors.transparent,
  systemNavigationBarDividerColor: Colors.transparent,
  systemNavigationBarContrastEnforced: false,
)
```

### 21.4 安全区处理

- `_AppMediaQuery`：顶部安全区超过屏高 30% 时钳到 20 dp（折叠屏/异形屏）。
- 页面、每个首页标签、`CommonRoute`、`CommonDesktopRoute`、open-container 路由都用 `KeyboardInsetHold` 包裹——否则上层对话框或 sheet 抬键盘时，每个后台页面都会逐帧重排。
- 读 `MediaQuery` 按方面（`paddingOf`、`viewInsetsOf`），**永不**用 `MediaQuery.of` 或对自身 context 用 `removePadding`——那会订阅每个 inset 变化。

### 21.5 键盘

- 软键盘每帧移动 `MediaQuery.viewInsets`，依赖它的东西会以帧率重建并重排数百毫秒。
- `ThemeManager` 用 `LayoutBuilder` 包住整个应用，这些重建在布局阶段运行。
- 底部 sheet 自己为键盘让位：键盘抬起 sheet 的脚，`_WithoutKeyboard` 从内容的 `MediaQuery` 里移除底部 inset。
- 键盘移动 sheet 时是"画出来的"：`_RenderSheetViewport` 把表面按视图那么高布局，与键盘无关；适配的 sheet 在键盘改变其上方空间时保持短内容布局时的限制，直到内容到达任一极限（`releaseLimit`）。

### 21.6 重建作用域

`LayoutBuilder` 把其下所有重建跑在自己的布局里，所以其中的 widget 重建会标记该 builder 需要布局，一直爬到最近的 relayout boundary。Flutter 会标记它布局过的每个渲染对象需要绘制，所以该路径上最近的 repaint boundary 也重绘。

所以在 `Scaffold` 主体的 `LayoutBuilder` 下，那是页面与其背景；在首页壳的 `LayoutBuilder` 下是整个首页路由——页面里转动的加载器，或 Dock 透镜移动，会每帧重排重绘整页，其上的玻璃每帧重新采集每个带，包括栏的，尽管栏下什么都没变。

所以主体与 Dock 各自在自己的作用域里接收重建：一个 `LayoutBuilder` 放在既是 repaint boundary 又是 relayout boundary 的边界下。

### 21.7 国际化

- 7 种语言：en、zh-CN、ja、ru、es、fr、ko。
- 界面字符串通过 ARB（`lib/l10n/intl/`）。
- `lib/ui` 组件自己的字符串走 `UiLocalizations`（`lib/ui/l10n.dart`），由 `AppUiLocalizations.delegate` 从 ARB 提供。
- CJK 字形回退见 §6.4。

---

## 22. 设计护栏

`test/lint/` 下 18 个测试守护这套体系：

| 测试 | 守护什么 |
|---|---|
| `superellipse_corners_test` | 禁止圆形圆角 API |
| `material_icons_test` | 禁止 `Icons.*` / `AnimatedIcons.*` |
| `typography_test` | 禁止裸 `fontSize` / `fontWeight` 与 `textTheme` |
| `design_package_test` | 只许 `material_ui`，禁 `flutter/material`、`cupertino` |
| `filled_button_glyphs_test` | 实心按钮内的 `GlyphIcon` 必须 `fill: 1` |
| `length_unit_test` | 注释、测试名、文档只许 dp 不许 px |
| `ui_layering_test` | `lib/ui` 不得外泄依赖，不得导入 Riverpod |
| `ui_layer_singleton_test` | `lib/ui` 不得触碰应用单例 |
| `icon_button_tooltip_test` | 图标按钮必须有 tooltip |
| `text_field_suggestions_test` | 只有秘密字段可请求密码键盘 |
| `circular_progress_test` | 圆形进度约束 |
| `dead_file_test` | 无未引用的公共声明 |
| `disposable_field_test` | 自建 controller / notifier / focus node 必须释放 |
| `scaffold_bottom_inset_test` | 脚手架底部 inset 规则 |
| `sheet_scroll_controller_test` | Sheet 滚动控制器归属 |
| `reorder_proxy_decorator_test` | 排序装饰器 |
| `dynamic_message_key_test` | 动态消息 key |
| `platform_layering_test` | 平台分层 |

### 22.1 注释密度

`tool/hooks/comment_density.sh` 在文件的**新增行**中独立注释超过 **5%** 时失败（忽略 20 行以下的小改动）。健康改动在 **3.6%** 或以下。

注释必须承载代码无法表达的东西：非显然的约束、被规避的上游行为、读者否则会搞错的原因、与别处定义的值的耦合。**永不**复述代码、叙述改动、逐行注释。

### 22.2 长度单位

Flutter 长度是逻辑像素，即 Android 的 dp。注释、测试名、文档、报告都给 dp，写 `48 dp`，**永不写 px**——读者会把 px 当成物理像素，把尺寸误判一个设备像素比。真在物理像素上工作时写完整的 "physical pixel"。

---

## 23. 附：token 速查表

### 圆角

```
AppCorner.none 0 · xs 4 · sm 8 · md 16 · lg 20 · xl 24 · xxl 28 · alert 34 · full 1000
AppRadius.{none,xs,sm,md,lg,xl,xxl,full}  ·  AppRadius.all/top/vertical
AppShape.{none,xs,sm,md,lg,xl,xxl,full,circle,input}  ·  AppShape.all/top/vertical/of
AppCorner.fit(shortestSide)
```

### 表面

```
SurfaceLevel.{page,card,block,inset}  ·  .next  ·  .colorOf(scheme)  ·  .floatingOf(scheme)
context.surfaceLevel  ·  context.raisedSurfaceLevel
SurfaceScope(level:, child:)
ColorScheme.chromeSurface  ·  ColorScheme.elevated
ElevatedSurfaceTheme  ·  ElevatedColorScheme
focusRingSide(scheme, {standsOff})  ·  focusRingStyle(...)  ·  FocusRing(shape:, child:)
```

### 颜色

```
seededColorScheme({seedColor, brightness, style})
ColorStyle.{vivid,tinted,soft,monochrome}
ColorScheme.{modalScrim, alertScrim, destructive, success, warning}
ColorScheme.{successContainer,onSuccessContainer,warningContainer,onWarningContainer}
ColorScheme.delayColor(int?)
Color.{opacity80,opacity60,opacity50,opacity38,opacity30,opacity15,opacity12,opacity10,opacity0}
Color.{tone, shiftTone, hex}
ColorScheme.toPureBlack(bool)
```

### 字体

```
AppTextStyle.{display,largeTitle,title1,pageTitle,title2,title3,headline,body,
              callout,subheadline,footnote,caption1,caption2}
AppTextStyle.x.of(context, {emphasized})
AppTextStyle.custom(context, size:, leading:, weight:)
TextStyle.{toLight,toLighter,toTabular,toJetBrainsMono}
context.{listTitleStyle,listSubtitleStyle,listCaptionStyle,cardHeaderStyle}
num.ap(context)  ·  num.mAp(context)
```

### 尺寸

```
listRowMinHeight 56 · listRowVerticalPadding 10
pageToolbarHeight 72 · sheetToolbarHeight 48 · sheetAppBarHeight 68
appBarActionSpace 4 · appBarActionInset(compact) 16/20
TonalButtonSize.{bar(44/24), section(36/20), compact(32/20)}
proxyGridSpacing 8 · profileItemMinWidth 300
maxMobileWidth 600 · maxLaptopWidth 840
```

### 动效

```
context.disableAnimations  ·  context.motionDuration(Duration)
SpringCurve(SpringDescription, seconds:)
fadeTabDuration 220 · midDuration 200 · commonDuration 300
```

### 图标

```
Glyph.size 24 · Glyph.strokeWidth 1.8
GlyphIcon(glyph, {size, color, fill})  ·  AnimatedGlyph(glyph:, filled:)
AppGlyphs.*  ·  AppGlyphs.backFor(platform)  ·  AppGlyphs.sidebar(progress)
```

### 仪表盘

```
DashboardColors.{page,card,tone,tone2,track,thumb,series2,ok,warn,...}
DashboardShadows.{e1,e2}
DashboardCorner.{desktopCard 32, mobileCard 34, block 20, mobileBlock 22,
                 tab 16, tableHeader 12, badge 14}
DashboardText.{cardTitle,mobileCardTitle,label,figure,unit,rowTitle}
DashboardDensity.{standard,compact,mobile}  ·  DashboardDensityScope
DashboardCard · DashboardCardHeader · DashboardBlock · DashboardChip
DashboardConnectedGroup · DashboardConnectedButton · DashboardSwitch
DashboardFadeOut · DashboardFitText · DashboardCappedRow · ShapeBadge
dashboardCount(int)  ·  dashboardTraffic(num)
```

### 面板

```
snapSheetDetents [9/16, 0.9]
showSheet · showSnapSheet · showFormSheet · showExtend
CommonDialog · InputDialog · CommonPopupBox · CommonPopupMenu · CommonPopupMenuItem
PopupPlacement.{overAnchorEnd, overAnchorStart, belowPoint}
LiquidGlassMode.{none,standard,ultra}  ·  LiquidGlassStyle.{regular,pill,menu,alert,tinted,lens,lensContent}
```

---

## 结语

> **Material 3 的骨架 + iOS 26/27 的皮与骨**：填充分层而非边框、超椭圆而非圆弧、Apple Dynamic Type 而非 Material 字号、自绘 121 个可填充字形而非图标字体、实测校准的 UIKit 弹簧而非固定时长、以及一套可选的三档 Liquid Glass 着色器。
>
> 所有细节都落到具名 token 上，由 18 个 lint 测试强制，**调用点不允许写字面量**。
