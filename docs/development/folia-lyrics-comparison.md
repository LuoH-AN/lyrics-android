# Folia 歌词显示差异与模式规划

研究日期：2026-11-20。上游为 [chthollyphile/folia-major](https://github.com/chthollyphile/folia-major)，固定读取 [`4a0c9f5`](https://github.com/chthollyphile/folia-major/tree/4a0c9f5a29a395f70f0962a895b52c271091e9cc)。本项目对照基线为 `578f148`，本次新增第一种展示方式「持续堆叠」。

## 歌词为什么看起来留得更久

用户描述的保留感有直接的源码依据，主要对应两条路径。

**Cappella 的消息堆叠**：[`cappellaMessageLayout.ts`](https://github.com/chthollyphile/folia-major/blob/4a0c9f5a29a395f70f0962a895b52c271091e9cc/src/components/visualizer/cappella/cappellaMessageLayout.ts) 的 `getVisibleMessages` 先取已经激活的消息，再从最新消息向前累加实际估算高度。超过视口预算后舍去更早的消息，同时有最低上下文与最大消息数限制。因此上一句不会因为唱完就消失，新的消息不断挤占可用空间。

**Fume 的长卷与镜头**：[`useFumeCanvasStage.ts`](https://github.com/chthollyphile/folia-major/blob/4a0c9f5a29a395f70f0962a895b52c271091e9cc/src/components/visualizer/fume/useFumeCanvasStage.ts) 遍历 `article.blocks`，仅跳过不在镜头视野内的块。[`fumeBlockFrame.ts`](https://github.com/chthollyphile/folia-major/blob/4a0c9f5a29a395f70f0962a895b52c271091e9cc/src/components/visualizer/fume/fumeBlockFrame.ts) 把唱过的块作为 `passed` 快照继续绘制；停留比例改变唱后样式，镜头移动才让这些块离开画面。长卷中的文字拥有固定世界位置，还支持总览镜头恢复旧文字的标准样式。

Folia 的其他模式各有自己的规则。Classic、云阶和 Tilt 主要保留当前句内已经唱过的词或段，下一句进入时仍会替换整句容器。Sonnet、Tempera、绘光和花火会编译分镜或段落程序，文字保留还受镜头与段落转场控制。

本项目原有 `popPhraseWindow` 在下一短语开始后最多保留上一短语 260 ms，随后停止绘制；`PopLyricsStage` 的 `AnimatedContent` 又在源歌词行改变时替换整句。这两层退场共同造成了当前的快速清屏感。关闭「短语退场」可以保留整句内部的文字，仍然无法保留跨句历史。

## 第一项已实现：持续堆叠

入口为 **设置 → 主页歌词 → 逐词跃动 → 歌词展示 → 持续堆叠**，也可直接应用「持续堆叠」动效方案。原有「短语切换」仍可选择，已有自定义设置不会因增加模式而重置。

| 项目 | 原短语切换 | 新持续堆叠 | Folia 对照 |
| --- | --- | --- | --- |
| 已唱文字 | 当前短语内保留 | 跨短语、跨源歌词行保留 | Cappella 累积消息；Fume 保留长卷块 |
| 旧段退出 | 新段开始后最多 260 ms 淡出 | 推移到视口外才停止绘制 | Cappella 按高度取舍；Fume 按镜头裁切 |
| 排版 | 每段在舞台中央独立适配，短段可放大 | 每段独立测量，放入固定连续布局，整体推移 | Fume 的固定布局与镜头分离 |
| 字号 | 短段最多按剩余空间放大 1.8 倍 | 固定基础字号，过大段落独立缩放适配 | 历史段落不随累积数量一起缩小 |
| 时钟 | 逐词与短语退场读取播放时间 | 逐词与整条布局推移读取同一播放时间 | 上游也按歌词时间驱动 |
| 快句 | 短语退场时间缩短 | 推移压缩到下一真实起点之前结束 | 不增加人工演唱延迟 |
| 同时起唱 | 不能拆成不同短语页 | 同一起点的段落共用一个推移目标 | 保留来源的同时演唱语义 |
| 暂停、跳转 | 定位当前短语 | 暂停冻结；跳转重建该时刻的历史布局 | 可按播放位置重建，而无需累积播放事件 |
| 间奏与曲末 | 仍受整句容器切换影响 | 保留最后的歌词画面，间奏进度单独显示 | Fume 长卷；Cappella 已激活消息 |
| 译文 | 当前句单独的字幕层 | 每句译文附在最后一段，一起移动与保留 | Cappella 原句消息；Fume 原有字幕策略不同 |
| 镜头 | 单词跟随、漂移、呼吸 | 固定视口，推移负责堆叠 | Fume 有额外二维机位、缩放和总览 |
| 操作 | 点击重听、长按完整原句分享 | 历史段落也能点击重听、长按分享完整原句 | 保留本项目播放器和分享行为 |

实现使用原生 Compose Canvas，复用现有分词、原始词时间、弹簧、长音和空间打包函数。`PopStackFlow` 只管理固定段落的视口位置。没有真实逐词时间的源歌词行仍整句显示；纯文本无时间轴内容继续使用滚动阅读。未引入上游运行时或素材。

保留范围按实测段落高度取约两屏上下文，仅绘制视口内段落。测量缓存最多 32 个源歌词行，旧缓存的淘汰不决定歌词何时退出画面。下一真实段落的几何信息提前准备，以便密集歌词的推移在下次起唱前结束；尚未起唱的段落不会提前显示。播放帧只更新绘制与可见边界，不重新分词、测量或排版。

### 可自定义内容

| 参数 | 范围与默认值 | 影响 |
| --- | --- | --- |
| 堆叠方向 | 向上／向下，默认向上 | 新歌词推动历史歌词的方向 |
| 新歌词落点 | 10–90%，默认 65% | 向上时为段落下沿，向下时为段落上沿；大段落自动适配 |
| 推移时长 | 0–1200 ms，默认 420 ms | 0 直接定位，密集起唱时自动缩短 |
| 段落间距 | 0–64 dp，默认 16 dp | 段落之间的空白 |
| 历史歌词亮度 | 0–100%，默认 80% | 叠加到词本身的唱后亮度；仍在演唱的重叠词保留演唱样式 |
| 边缘渐隐范围 | 0–80 dp，默认 24 dp | 渐隐发生在视口边缘；0 关闭 |
| 每段长度 | 4–24 字，默认 10 | 仍优先词组、标点和真实停顿边界 |
| 每段目标行数 | 1–4，默认 2 | 调整每段的宽度容量，并结合每段长度限制 |
| 译文随歌词堆叠 | 默认开启 | 双语模式下与原句一起保留 |

共享的逐词／逐字、错落／居中／阶梯、字体大小与字重、词组内外间距、行距、单行长度、错落幅度、动态防重叠、入场方向与时长、提前量、飞入距离、当前词放大、旋转、长音抖动、行级运动、唱后停留与亮度仍可调。模式与全部堆叠参数进入命名方案存储，旧方案缺少新字段时使用兼容默认值。

## 上游模式清单与差异

已核对 [`registry.tsx`](https://github.com/chthollyphile/folia-major/blob/4a0c9f5a29a395f70f0962a895b52c271091e9cc/src/components/visualizer/registry.tsx) 自动发现的 18 个内置模式入口。下表的路径均相对上游 `src/components/visualizer/`。前九类核对了歌词容器、保留路径或位置规则；涉及复杂图形运行时的模式核对了入口、程序编译及公开调节字段，具体着色器与图形素材列入对应模式的后续专项工作。

| 模式 | 显示组织与已核对差异 | 代表性自定义内容 | 核对路径 |
| --- | --- | --- | --- |
| Classic / Luminous | 当前句逐词弹簧、旋转与发光，整句容器切换；本项目已有相近词动画 | 旋转、词间距、呼吸倍率、防重叠 | `classic/Visualizer.tsx`、`classic/tuning.ts` |
| Partita / 云阶 | 当前句拆成多栏、栏内 chunk；当前错落打包仍是单舞台行布局 | 导引线、语义分组、错落最小／最大值、防重叠 | `partita/VisualizerPartita.tsx`、`partita/tuning.ts` |
| Fume | 固定长卷、逐块打印、passed 快照和跟随／总览镜头；新堆叠采用线性连续布局 | 停留比例、镜头速度与方式、发光、主块缩放、背景对象透明度 | `fume/useFumeCanvasStage.ts`、`fume/fumeBlockFrame.ts` |
| Cappella | 已激活歌词变成聊天消息，按高度保留最新历史；新堆叠保留相同核心生命周期 | 表情消息、头像来源和自定义包 | `cappella/cappellaMessageLayout.ts`、`cappella/tuning.ts` |
| Tilt | 当前句多个倾斜段依次显现，下一句容器约 450 ms 淡出；新堆叠跨源行保留 | 分段概率、倾斜样式概率、配色 | `tilt/VisualizerTilt.tsx`、`tilt/tuning.ts` |
| Monet | 海报与歌词列分工，实测行高定位，支持人工浏览与边缘遮罩；本项目滚动模式缺少这种海报编排 | 字号、镜像、肖像样式、描述、关键词配色、音频条 | `monet/MonetLyricsRail.tsx`、`monet/monetRailLayout.ts` |
| Pendolo | 歌词围绕表盘焦点、弧线和刻度组织，支持人工滚动锚点 | 弧半径与角度、焦点缩放、镜像、齿轮、倾角、深度 | `pendolo/VisualizerPendolo.tsx`、`pendolo/tuning.ts` |
| Still | 静态显示当前句与前后相邻句，保留上下文但没有持续累积的历史 | 共享字体与字幕设置 | `still/VisualizerStill.tsx` |
| Claddagh | 当前句沿圆环／椭圆组织，与轴线和节拍联动 | 半径、椭圆倾角、位置、字间距、圆环译文 | `claddagh/VisualizerCladdagh.tsx`、`claddagh/tuning.ts` |
| Cadenza / Mindscape | 当前／下一句预排成片段和落点，DOM 与 Canvas 同步；已唱词有残留漂移 | 模式自己的设置面板与共享字体／主题 | `cadenza/VisualizerCadenza.tsx`、`cadenza/entry.tsx` |
| Diorama / 镜台 | 文字置于三维空间沿路径编排，摄影机穿行；需要独立三维场景 | 摄影机、空间运动、粒子、发光、文字强调、渐变 | `diorama/VisualizerDiorama.tsx`、`diorama/tuning.ts` |
| Sonnet / 商籁 | 确定性 PV 时间轴与分镜，片段视觉尾部受下一句限制 | 镜头、排版运动、导引、巨型装饰字、边框、后处理 | `sonnet/sonnetProgram.ts`、`sonnet/tuning.ts` |
| Tempera | 块状 PV；可按词与约 2.2 秒分镜容量切块，也可整句保留在同一块 | 字形运动、整句开关、落稳拉伸、深度、景深、色彩与装饰 | `tempera/temperaProgram.ts`、`tempera/tuning.ts` |
| Lumiere / 绘光 | 整首歌词编译为段落与光照镜头，长间奏有独立镜头 | 光照、雾、发光、邻句范围、衰减、回声、轨迹、无缝转场 | `lumiere/lumiereProgram.ts`、`lumiere/tuning.ts` |
| Hanabi / 花火 | 段落镜头与烟花世界共同上升，间奏继续编排场景 | 烟花数量、上升速度、旋转、文字运动、发光、副歌、拼贴 | `hanabi/hanabiProgram.ts`、`hanabi/tuning.ts` |
| Metaphor / 隐喻 | 播放时间选 cue 和镜头，字形与融球合成墨场；按需保留当前与相邻 cue 场景 | 反相、翻转、关键词着色、转场 | `metaphor/createMetaphorRuntime.ts`、`metaphor/tuning.ts` |
| Nebbia / 雾笺 | 整首预排，玻璃写字与雨／雪／窗外场景互动，运行时支持按进度补画 | 窗外背景、玻璃、雨、制作信息、水母 | `nebbia/createNebbiaRuntime.ts`、`nebbia/tuning.ts` |
| Machi / 町景 | 街景三维运行时，歌词、字幕与环境镜头配合 | 场景、时间、天气、摄影机、运动、景深、焦点 | `machi/VisualizerMachi.tsx`、`machi/tuning.ts` |

## 后续逐个实现的顺序

1. **持续堆叠：本次完成。** 固定跨句布局、视口退出、原始歌词时钟、参数和命名方案是后续模式共用的基础。
2. **长卷跟随：接近 Fume。** 增加二维固定词块位置、镜头机位／缩放／总览；自定义词块宽度、纵横间距、焦点位置、跟随速度、停留样式和总览时机。
3. **多栏云阶：接近 Partita。** 增加真正的多栏结构；自定义栏数、栏宽、栏间距、错落范围、导引线、块对齐和防重叠。
4. **会话堆叠：接近 Cappella。** 在持续堆叠上增加气泡和左右角色；自定义角色、头像、气泡宽度与圆角、时间标识、译文位置和可选表情。
5. **海报阅读与静态阅读：接近 Monet／Still。** 自定义封面区域、歌词列、前后文范围、当前行比例、译文排列、人工浏览和渐隐。
6. **倾斜字排与圆环字排：接近 Tilt／Claddagh。** 自定义分段、字号层次、倾角、半径、椭圆度、字间距和字幕位置。
7. **表盘与镜台：接近 Pendolo／Diorama。** 分别处理弧线与三维路径；按设备提供适当的画质与运动控制。
8. **PV、光照、烟花与环境模式。** Sonnet、Tempera、绘光、花火、隐喻、雾笺和町景按各自运行时专项拆解；每次实现一种，分别明确文字的进入、停留、退出以及镜头和背景参数。

每个新模式提供直接可选的展示入口、实时预览、模式专属设置、共享字体／时钟与命名方案。首批优先把文字布局、停留与镜头做好，再为相应模式增加背景和图形效果。

## 验证与交付边界

已经执行：7 项纯 Kotlin/JVM 堆叠行为测试，覆盖保留、视口退出、暂停／跳转、快速和同时起唱、反向移动、变高段落、间距与小屏；主页与 Compose 界面边界回归；Kotlin 编译器解析器语法检查。

新增 Android／Compose 回归覆盖跨句历史、间奏、换歌清理、双语堆叠、译文开关、完整原句分享以及设置／命名方案保存。按照本次暂不构建 Nightly 的要求，没有运行 Android 构建、Robolectric 或实机视觉测试；上述原生回归尚待后续 CI 执行，实际视觉节奏与性能需要在 APK 上确认。
