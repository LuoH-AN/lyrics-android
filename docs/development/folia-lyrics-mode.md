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

## 当前实现的资源边界

本模式直接复用 `LyricDocument / LyricWord / LyricClock`，新增 `PopLyricTimeline`、`PopLyricPacking` 与 `ComposePopLyrics`。未引入 Folia 的运行时、依赖或素材；上游采用 AGPL-3.0，直接搬运代码时需保留并遵守其许可。

| 项目 | 实现 |
| --- | --- |
| 排版范围 | 当前句及最多一条退场句；每句最多 64 个独立词、256 个文字代码单元，超过后整句显示；长句的舞台预览测量最多 512 个代码单元，正文保留 |
| 字体测量 | 当前句、尺寸、字号、字重变化时测量；缓存 `TextLayoutResult` 与坐标 |
| 帧内工作 | 读取时钟、查预计算动画表、变换与绘制可见词 |
| 重组范围 | 绘制时钟只在 Canvas 与间奏进度中读取，镜头时钟只在 graphicsLayer 中读取；句子、设置或控件尺寸变化时更新页面 |
| 刷新 | 逐词绘制至多约 30 Hz；镜头移动时单独以约 60 Hz 更新图层，稳定后停止；启用舞台呼吸时稳定文字阶段使用约 20 Hz 的时钟；默认关闭呼吸，文字和跟随镜头停稳后等待下一真实时间点 |
| 停止条件 | 暂停、主页不活动、切换显示模式、播放结束 |
| 背景 | 复用已有的小尺寸预模糊封面，与歌词共享镜头时钟，预留 overscan 防止移动露底；不重新模糊每一帧 |
| 图形效果 | 原生 Canvas 加共享镜头 graphicsLayer，整句使用有界 AnimatedContent 过渡；无逐字模糊、发光或粒子 |
| 内存 | 不栅格化整首歌词，不缓存整首歌的文字纹理，不为每个词创建动画状态对象 |

歌词绘制与镜头采样由同一个调度器驱动，镜头更新不会增加文字测量、排版或整页重组。30 Hz 上限针对本模式的歌词绘制；控制栏与应用其他动画仍遵循现有逻辑。绘制 API 可能产生内部对象，本实现没有声称整条渲染路径零分配。

排版先为放大和轻微旋转预留空间，再按舞台宽高整体适配。旋转由文字和序号确定；进度变化不会改变坐标。没有独立时间轴时只显示整句，不按字数编造演唱时间。中文模式的译文、RTL 文字、损坏或不匹配的逐字数据同样走整句绘制。点击重听、长按分享与双语保持可用，译文紧跟测量后的歌词下沿，不固定在舞台底部；舞台不再显示浏览／返回入口，完整歌词可通过设置选择滚动模式或摘句分享查看。

## 动效与自适应控制栏

原生动效参考上游的 150 ms 预入场、0.5 倍初始缩放、当前词放大、唱后回落与 0.82 透明度；当前直接默认参数将当前词放大设为 1.3 倍。用 stiffness=200、damping=20 的单位质量弹簧响应预生成查找表，默认入场 600 ms，可在 200–1000 ms 调整，查找表样本之间线性插值；唱后回落 500 ms，使用端点零速度的缓动。固定种子的偏移和旋转在布局时计算，空间预留包含入场旋转；不使用逐词模糊和发光。因手机资源边界和字体栅格化差异，未声称与网页逐像素一致。

歌词默认使用适中的舞台字号、居中换行和较紧凑的行间距，可选错落／居中／阶梯排版；放大和旋转预留范围由实际设置计算。当前句的文字测量结果按字体与屏幕配置缓存，控制栏伸缩只重新打包坐标。`PopLyricsStyle` 直接提供默认参数，不定义主题或命名预设；入场方向、速度、提前量、距离、放大和旋转分别可调；共享镜头支持固定、漂移、跟随，以及独立幅度和舞台呼吸开关。弹簧响应和可选呼吸波形在初始化时生成查找表。跟随轨迹由当前句已测量的文字中心坐标生成，负向平移将当前词带向视野中心；跟随使用 400–900 ms 的三次曲线，并从上一段轨迹的实际位置和速度连续接续；回中时间延长为跟随的 1.25 倍，不按词序号交替抖动、旋转或脉冲缩放。镜头范围按整句放大后的文字边缘约束，避免短句贴边或大字被推到屏幕外；唱完后用同一平滑插值回到居中位置。默认跟随幅度 70%，关闭呼吸。逐字显示保留原词真实起止时间，增加最多 100 ms 的视觉入场错位；复杂 emoji 不拆开，组合字符保留，超过 64 个单元时保留原始词单元。共享同一原词起止时间的字符合并为一个镜头焦点，避免逐字入场时反复启动镜头。旧命名预设一次性迁移为当前直接默认值，明确保存的自定义参数保留。

播放栏在横竖屏分别保存大小与停靠状态。竖屏完整与中等栏恢复屏宽减 32 dp，不额外限高；横屏完整栏最多 340 dp、中等栏最多 300 dp，且均不超过可用宽度 42%。横屏完整栏占据可用高度减上下各 16 dp，头部与播放按钮在内部居中成组，间距 12 dp。完整与中等栏的宽高由同一手势进度连续插值，松手时从拖动位置继续缓动；两种形态共享固定的歌词侧边预留，避免拖动每帧重新打包文字。每次进入横屏重置为右下角 56 dp 圆形封面；返回竖屏恢复原来的竖屏状态。播放栏去掉底板与边框，封面背景一次性迁移为默认关闭，可手动重新开启。中等栏双向横滑收拢并将封面移至对应角落；封面向内滑恢复中等栏，长按与无障碍动作也可展开。上滑依次从圆形到中等、完整，下滑按相反顺序收起；进度条水平拖动不触发收拢。镜头只变换歌词和背景，播放控件触点保持稳定。

## 验证与后续实机测量

`PopLyricTimelineTest` 覆盖时序、跳转后的状态、空白与组合字符、错误数据、动画数量上限、动效边界及横竖屏空间预留；`ComposePopLyricsTest` 覆盖模式热切换、精简导航、暂停更新、双语、间奏和摘句。

按仓库规范，Node 回归在开发环境运行；Android 编译、JUnit 与 Robolectric 在 GitHub Actions 运行。实机验证应包含 Android 8、常用中端机与高刷新率设备，分别播放密集逐字、长音和长句，并测量帧耗时、主线程工作、分配、内存与功耗。重点确认固定镜头且关闭呼吸时长音期间不持续重绘，启用呼吸时共享时钟受到刷新上限约束、暂停和进入后台后没有歌词帧回调，以及反复切歌不累积布局对象。上述资源边界是实现约束，不能代替设备上的性能与功耗测量。
