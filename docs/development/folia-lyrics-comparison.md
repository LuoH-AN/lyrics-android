# Folia 歌词显示差异与模式规划

研究日期：2026-11-20。上游为 [chthollyphile/folia-major](https://github.com/chthollyphile/folia-major)，固定读取 [`4a0c9f5`](https://github.com/chthollyphile/folia-major/tree/4a0c9f5a29a395f70f0962a895b52c271091e9cc)。初次研究的本项目对照基线为 `578f148`。当前逐词跃动使用短语与整句切换，保留后续的动画及镜头平滑改动。

## 歌词为什么看起来留得更久

用户描述的保留感有直接的源码依据，主要对应两条路径。

**Cappella 的消息堆叠**：[`cappellaMessageLayout.ts`](https://github.com/chthollyphile/folia-major/blob/4a0c9f5a29a395f70f0962a895b52c271091e9cc/src/components/visualizer/cappella/cappellaMessageLayout.ts) 的 `getVisibleMessages` 先取已经激活的消息，再从最新消息向前累加实际估算高度。超过视口预算后舍去更早的消息，同时有最低上下文与最大消息数限制。因此上一句不会因为唱完就消失，新的消息不断挤占可用空间。

**Fume 的长卷与镜头**：[`useFumeCanvasStage.ts`](https://github.com/chthollyphile/folia-major/blob/4a0c9f5a29a395f70f0962a895b52c271091e9cc/src/components/visualizer/fume/useFumeCanvasStage.ts) 遍历 `article.blocks`，仅跳过不在镜头视野内的块。[`fumeBlockFrame.ts`](https://github.com/chthollyphile/folia-major/blob/4a0c9f5a29a395f70f0962a895b52c271091e9cc/src/components/visualizer/fume/fumeBlockFrame.ts) 把唱过的块作为 `passed` 快照继续绘制；停留比例改变唱后样式，镜头移动才让这些块离开画面。长卷中的文字拥有固定世界位置，还支持总览镜头恢复旧文字的标准样式。

Folia 的其他模式各有自己的规则。Classic、云阶和 Tilt 主要保留当前句内已经唱过的词或段，下一句进入时仍会替换整句容器。Sonnet、Tempera、绘光和花火会编译分镜或段落程序，文字保留还受镜头与段落转场控制。

本项目 `popPhraseWindow` 在下一短语开始后按歌词节奏短暂保留上一短语，随后停止绘制；`PopLyricsStage` 的 `AnimatedContent` 又在源歌词行改变时替换整句。这两层退场决定当前文字的替换方式。关闭「短语退场」可以保留整句内部的文字，仍然无法保留跨句历史。

## 当前逐词跃动

当前舞台使用原生 Compose Canvas，按原始歌词时间驱动逐词入场、短语和整句替换。唱完的文字保留在当前短语或整句内，跨源歌词行的历史不持续累积。双语译文位于当前句下方，点击重听和长按分享完整原句继续可用；无逐词时间的歌词整句显示，无时间轴内容使用滚动阅读。

整句与短语切换共用淡入淡出、滑动、缩放、四周聚拢、四周散开和稳定随机效果。滑动方向支持左、右、上、下、左右随机、上下随机及四向随机。逐词入场保留多向移入、向上跳出、原地弹出、聚拢、散开及随机；柔和起步、轻微回弹与完整收尾减少骤停。

镜头支持固定、漂移、跟随文字和环绕跟随，相邻词和句子衔接时延续位置、速度与加速度，移动边界平滑插值，长间奏平滑回中。整句与短语转场现在读取相同的播放时钟，自动方向参考边界时的屏幕位移，缩放与聚散沿该走势带少量位移。排版支持错落、居中、阶梯、波浪和稳定随机，继续使用实际文字测量及动态间距。

字体、词组内外间距、行距、行宽比例、错落幅度、短语长度、入场时长与提前量、入场位移、当前词缩放、旋转、长音振幅、行级运动、高亮保持及亮度都可自定义。新增整体强调力度、节奏自适应、叠加协调、转场联动及舞台／字幕留白选项，见 [动效协调与排版](lyric-motion-orchestration.md)。内置方案扩充至 9 套，以「均衡流动」为推荐默认，覆盖静阅、轻柔、波形、环绕、横向推移、中心聚合、节奏与自由变换。原有内置 ID 保持兼容，用户命名方案继续可用。旧展示字段会被忽略，旧方案的其余动效与文字参数继续保留。

## 上游模式清单与差异

已核对 [`registry.tsx`](https://github.com/chthollyphile/folia-major/blob/4a0c9f5a29a395f70f0962a895b52c271091e9cc/src/components/visualizer/registry.tsx) 自动发现的 18 个内置模式入口。下表的路径均相对上游 `src/components/visualizer/`。前九类核对了歌词容器、保留路径或位置规则；涉及复杂图形运行时的模式核对了入口、程序编译及公开调节字段，具体着色器与图形素材列入对应模式的后续专项工作。

| 模式 | 显示组织与已核对差异 | 代表性自定义内容 | 核对路径 |
| --- | --- | --- | --- |
| Classic / Luminous | 当前句逐词弹簧、旋转与发光，整句容器切换；本项目已有相近词动画 | 旋转、词间距、呼吸倍率、防重叠 | `classic/Visualizer.tsx`、`classic/tuning.ts` |
| Partita / 云阶 | 当前句拆成多栏、栏内 chunk；当前错落打包仍是单舞台行布局 | 导引线、语义分组、错落最小／最大值、防重叠 | `partita/VisualizerPartita.tsx`、`partita/tuning.ts` |
| Fume | 固定长卷、逐块打印、passed 快照和跟随／总览镜头；本项目按短语／整句替换文字，没有固定长卷 | 停留比例、镜头速度与方式、发光、主块缩放、背景对象透明度 | `fume/useFumeCanvasStage.ts`、`fume/fumeBlockFrame.ts` |
| Cappella | 已激活歌词变成聊天消息，按高度保留最新历史；本项目没有消息式历史布局 | 表情消息、头像来源和自定义包 | `cappella/cappellaMessageLayout.ts`、`cappella/tuning.ts` |
| Tilt | 当前句多个倾斜段依次显现，下一句容器约 450 ms 淡出；本项目共享整句转场并支持聚散与随机效果 | 分段概率、倾斜样式概率、配色 | `tilt/VisualizerTilt.tsx`、`tilt/tuning.ts` |
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

## 其他模式的实现差异

当前波浪和随机排版仍是单舞台布局，尚未提供 Partita 的真正多栏结构、Monet 的海报歌词列、Tilt 的独立倾斜段落或 Claddagh 的圆环字排。Pendolo 的弧线与刻度、Diorama 的三维路径，以及 PV、光照、烟花和环境模式仍需要各自的专用运行时。

这些上游模式保留为研究对照。当前自定义入口覆盖逐词跃动的文字、节奏、布局与镜头参数，具体选项见 [歌词外观与双语显示](../guide/lyrics-display.md)。

## 验证范围

保留逐词时间、短语与整句切换、随机运动、镜头连续性、纵横屏原生渲染以及设置／命名方案的回归检查。旧配置兼容检查覆盖展示字段清理、其余自定义参数保留、旧方案读取和重新保存。Android 构建与原生测试在 GitHub Actions 执行，实际设备上的视觉节奏与帧率仍需通过 APK 确认。
