# Folia 逐词动效与手机端实现

调研参考 [chthollyphile/folia-major](https://github.com/chthollyphile/folia-major)，读取版本为 `77d52a3168ab2608174d2e5ad6d51b7bc8befd11`。当前实现是本项目的原生 Compose 显示模式「逐词跃动」。

## 上游如何让词逐个跳出

- [`GlowWord.tsx`](https://github.com/chthollyphile/folia-major/blob/77d52a3168ab2608174d2e5ad6d51b7bc8befd11/src/components/visualizer/GlowWord.tsx) 根据播放器时间，把每个词切换为 `waiting / active / passed`。只在状态变化时更新 React 状态。
- [`glowWordVariants.ts`](https://github.com/chthollyphile/folia-major/blob/77d52a3168ab2608174d2e5ad6d51b7bc8befd11/src/components/visualizer/glowWordVariants.ts) 让未唱词透明且缩小，当前词用弹簧移动、放大和旋转，唱完后回落；正文和发光层分别处理。
- [`partitaLayout.ts`](https://github.com/chthollyphile/folia-major/blob/77d52a3168ab2608174d2e5ad6d51b7bc8befd11/src/components/visualizer/partita/partitaLayout.ts) 是「云阶」的错落分栏排版，按歌词、参数和窗口尺寸缓存布局，并预热下一句。偏移用固定种子生成，播放期间不会重新随机排版。
- [`cjkSemanticLayout.ts`](https://github.com/chthollyphile/folia-major/blob/77d52a3168ab2608174d2e5ad6d51b7bc8befd11/src/utils/lyrics/cjkSemanticLayout.ts) 将语义分组对齐到原始逐字数据，并附着标点；不能精确对齐时保留原始词单元。
- [`graphemeTiming.ts`](https://github.com/chthollyphile/folia-major/blob/77d52a3168ab2608174d2e5ad6d51b7bc8befd11/src/utils/lyrics/graphemeTiming.ts) 把词与音节时间映射到完整字符簇，避免把组合字符当作多个独立文字。

- [`classic/Visualizer.tsx`](https://github.com/chthollyphile/folia-major/blob/77d52a3168ab2608174d2e5ad6d51b7bc8befd11/src/components/visualizer/classic/Visualizer.tsx) 和「云阶」还会对整个歌词容器应用上下漂浮及呼吸缩放，不仅动画单个词。
- [`fumeCameraStep.ts`](https://github.com/chthollyphile/folia-major/blob/77d52a3168ab2608174d2e5ad6d51b7bc8befd11/src/components/visualizer/fume/fumeCameraStep.ts) 驱动视点跟踪；文字和背景使用关联的镜头变换。

这些动画依靠歌词时间轴触发。音频分析、背景和粒子是额外的视觉层，并非逐词入场的前提。

## 当前跃动实现（2026-11-14 调整）

本模式复用 `LyricDocument / LyricWord / LyricClock`，使用原生 Compose Canvas、缓存的文字测量和预计算运动曲线。未引入 Folia 的运行时或素材。逐字和词组布局保持原始演唱时间，中文分组使用 Android ICU；没有真实逐词时间、译文或复杂 RTL 内容仍使用整句显示。

### 刷新与绘制

文字与镜头由同一个 `withFrameNanos` 更新，移除原来的文字 30 Hz、镜头 60 Hz 和呼吸 20 Hz 门限。前台播放时通过窗口的显示模式和刷新率首选项请求同分辨率下最接近 120 Hz 的模式，暂停、离开页面或关闭空间动效时恢复原窗口设置。120 Hz 面板可每帧更新；实际刷新率和帧耗时仍取决于系统及设备，未做实机帧率测量。

默认开启轻微呼吸，呼吸查找表增加线性插值。关闭呼吸后，文字与镜头停止运动时仍等待下一真实时间边界。暂停和后台停止歌词帧循环。保留当前、下一句和退场句的有界测量及场景缓存，下一句仍提前 180–1200 ms 预热，不在每帧重新分词或测量。

### 排版与文字力度

默认错落排版，词组共享基础大小，按稳定种子与原始长音时长形成大小层次。居中模式保持均衡行宽；错落模式使用交替长短的目标行宽及更明显的左右落点；阶梯模式渐变目标行宽与缩进。组内字保持一致的基础大小，布局不随播放进度重新随机。

舞台字重在通用字重基础上增加 200（上限 900），字号增加 10 sp，文字行高为 1.15 倍。旋转、词组基础大小、弹簧过冲和行级位移均计入空间预留，长句整体适配可用舞台。动态防重叠、词组内外间距、行距、单行长度及错落幅度仍可单独调节。

### 长句分页

逐词歌词默认按小段分页，每段容量默认约 10 个中文字，并根据舞台宽高、字号及动态空间预留进一步缩短，目标为每页一到两行。分段优先使用词组边界、标点和真实停顿，避免短尾页及过快切换。每页分别排版、适配和建立镜头轨迹，避免整句挤在同一页后整体缩小。设置新增「长句分页」和「每段长度」（4–24 字）；拉小「单行长度」同时缩短可用行宽及分页容量。

页面仅在下一段的真实演唱起点切换。共享时间的字符、尚未结束的重叠长音保持同页；没有逐词时间、译文及 RTL 内容保留整句显示。来源把整个短语记作一个词时，ICU 可以拆开其视觉排版，但每个片段保留来源的完整起止时间，不推算逐字时间。暂停、拖动进度及关闭空间动效时也按播放位置选页，长按分享仍保留完整原句。

「单行长度」原先在屏幕宽度限制之前乘倍率，手机上经常被屏幕宽度截住；现在先确定基准行宽，再应用倍率，并同步影响长词及整句的文字测量宽度。测量、分段和各页场景仍按当前/下一/退场句缓存，播放帧只绘制当前页，跨页才触发 Compose 内容切换。

### 运动与强调

默认入场 420 ms、提前 90 ms、当前词放大 145%、旋转幅度 10°、四周飞入距离 100%、唱后亮度 72%。使用单位质量、stiffness=260、damping=20 的弹簧查找表，保留位移超出目标后返回的完整回弹。65 ms 淡入，唱后在 280 ms 内回落到词组基础大小，颜色连续回到正文色；当前正在演唱的文字使用缓存的轻量阴影强调。

密集词和短句仍压缩入场时长，同时将提前量限制到入场时长的四分之一，避免动画峰值先于演唱。普通句切换为 180 ms，默认轻微缩放切换，新句透明度在前半段完成；极短句直接显现。行级运动默认 65%，包含侧向进入、上移和缩放，词级运动继续按真实时间触发。

镜头默认幅度 80%、速度 120%，基础跟随窗口约 267 ms，并按相邻词视觉起点继续收缩。文字和镜头使用同一份视觉提前量，交接保留当前位置与速度，唱后平滑回中。背景继续独立于歌词镜头，封面换图保留原有淡化，播放器控制栏逻辑保持原有设计。

升级时将 `pop_defaults_revision` 提升到 3，一次性应用这套新跃动参数；应用后用户再次调整的参数正常保存。通用阅读设置保留，新的字重与字号增量仅用于跃动舞台。

## 本次交付

按维护者要求，使用快速 `assembleCompact` CI，不运行 Node、JUnit、Robolectric 或截图任务。CI 仅编译、打包、校验 APK 安装信息并上传产物。现有测试保留在仓库，但本次没有运行，不声称已验证实际帧率或实机视觉效果。
