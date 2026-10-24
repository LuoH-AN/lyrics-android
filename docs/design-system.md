# Agora 风格的 Material 设计

参考源码：[newo-ether/Agora](https://github.com/newo-ether/Agora)，版本 `239eaa93a08412f79495d711ed3b05f495f0127e`。实现从歌词项目的 `main`（`7ae9772`）开始，没有合入 `feat/apple-material-ui` 的 Squircle、滑动分段或自定义按压组件。

## 设计来源与对应实现

| Agora 源码 | 歌词项目中的对应规则 |
| --- | --- |
| `ui/theme/Color.kt`、`Theme.kt` | 默认 FOREST 种子 `#2E7D32`，Tonal Spot、对比度 0；浅深色完整的 primary / secondary / tertiary、容器、描边、错误和反色角色 |
| `ui/theme/Type.kt` | 同一 MiOutfit 可变字体；正文 16sp、说明 13sp、列表标题 19sp、展开页标题 33sp、收起标题 22sp |
| `ui/settings/SettingsScreen.kt` | 24dp 内容边距；组标题为 primary 色；组内 2dp 间距，外角 24dp、内角 5dp；无堆叠阴影 |
| `ui/settings/SettingsScaffold.kt` | 154dp 随正文滚动的标题空间、固定 64dp 顶栏；单个标题以 66dp 行程线性上移，x/scale 用原文 (.2,0,.5,1) 曲线；内容最大宽度 840dp |
| `ui/settings/SettingsAnimations.kt` | 同窗口双页面切换；75% 屏宽位移、0.94→1 入场缩放；直接使用 Compose `DampingRatioLowBouncy`（0.75）和 `StiffnessLow`（200），新页 300ms 淡入，旧页弹簧淡出；新页在切换中可点击，旧页及裸露区域拦截触摸 |
| `ui/components/SmoothBottomSheet.kt` | 面板弹簧阻尼 0.9、刚度 350；28dp 顶角、最大宽度 640dp、最大高度 94%、0.32 遮罩；Material 负责拖动、返回和点外部关闭 |
| `ui/components/AgoraOptionHighlight.kt` | 面板选项采用透明底、24dp 涟漪高亮，图标与文案对齐 |
| `ui/motion/AgoraMotionPolicy.kt` | 关闭系统动效时取消面板空间移动；导航保留交叉淡入淡出 |

参考的设计规则以原生 Android Views 实现。歌词逐字绘制、播放时钟、同步偏移和翻译规则保留；播放控制栏使用相同的表面、字体和颜色角色。

“更多”面板的遮罩绘制在同一视图层，透明度逐帧跟随面板的可见比例。关闭时面板离开屏幕便立即移除弹窗，不等待弹簧尾部收敛，也不叠加系统窗口遮罩的延迟退场。

页面切换依据同一参考版本的实际导航源码，使用同一个窗口内的 Fragment / View 容器，不再用 Activity XML 动画或采样插值曲线。动画核心直接使用 Compose `Animatable`、`spring` 和 `tween`，不再把它们转写成 AndroidX `SpringAnimation` / `ValueAnimator`。

- `SettingsOverlayHost.kt`：主页进入设置等顶层功能时保留主页；浮层的不透明背景独立于内页切换，随主题更新，填满两页位移、缩放与透明度留下的空隙；新覆盖层从屏宽 25% 处、0.92 倍缩放进入，位移和缩放使用阻尼 0.75 / 刚度 200 的实时弹簧，透明度在 300ms 内淡入；独立遮罩为 0.45，延迟 50ms 后在 300ms 内淡入。退出在 400ms 内使用 FastOutSlowIn 缓动移至屏外、缩至 0.94 并同步淡出遮罩和内容。
- `ui/settings/SettingsAnimations.kt` / `GuardedAnimatedContent`：分类与子页面共用双页面容器。新页以 75% 屏宽位移、0.94 倍缩放进入；新页透明度用 300ms 默认 tween，旧页透明度、两页位移与缩放使用阻尼 0.75 / 刚度 200、可见阈值 0.001 的弹簧。绘制时夹紧透明度、位移取整。初始页面不播放入口动画。
- 新页立即接收触摸；旧页拦截子控件触摸，位于两页之间的透明屏障防止裸露区域点穿。连续导航取消上一轮动画，只留下当前新旧两页，动画全部结束再移除旧页、把新页归位。
- 导航栈保留目的地；会话级最多 64 项缓存按导航路径保留滚动位置，重新进入分类也恢复位置，关闭浮层清空；页面控件按需重建；页面有独立 Fragment 生命周期，退出预览停止、移除后任务取消。Activity 保留为系统和悬浮窗的入口，应用内部不再逐页启动 Activity。
- 系统三项动画设置均被观察，任一比例为零时立即取消空间移动与缩放，不重播入场。animator 比例仍非零时保留交叉淡入淡出；animator 比例为零时 Compose 自身立即完成动画。这里实现的是 Agora 的点击触发、可中断导航，不增加源码没有提供的手指拖动导航。

`Spring.DampingRatioLowBouncy` 是 **0.75**，`DampingRatioMediumBouncy` 才是 0.5；前者理论回弹约为运动距离的 2.84%，后者约 16.3%。以前把这两个常量混淆，放大了回弹，已纠正。导航直接引用 Compose 的具名常量，避免再次转写出错。

每个导航容器只有一个 `BroadcastFrameClock`，由 Android `Choreographer` 发送同一个帧时间戳，位移、透明度和缩放用一个结构化协程树运行、取消和等待结束；不再分别使用两个动画引擎计时、每帧估算透明度速度或手动累计六个完成回调。取消时由 `Animatable` 清掉速度，保留当前值，避免快速返回时把入场速度带进出场、继续放大或变亮。页面仍使用原生 Material 控件与独立生命周期。取消额外 preDraw 等待，动画从 Compose 的第一个帧时间戳开始；这消除了独立同步点，但实机耗时尚未测量。

原文：[覆盖层](https://github.com/newo-ether/Agora/blob/239eaa93a08412f79495d711ed3b05f495f0127e/app/src/main/java/com/newoether/agora/SettingsOverlayHost.kt)、[页面切换](https://github.com/newo-ether/Agora/blob/239eaa93a08412f79495d711ed3b05f495f0127e/app/src/main/java/com/newoether/agora/ui/settings/SettingsAnimations.kt)、[导航状态](https://github.com/newo-ether/Agora/blob/239eaa93a08412f79495d711ed3b05f495f0127e/app/src/main/java/com/newoether/agora/ui/settings/SettingsScreen.kt)、[滚动保留](https://github.com/newo-ether/Agora/blob/239eaa93a08412f79495d711ed3b05f495f0127e/app/src/main/java/com/newoether/agora/ui/settings/SettingsScrollRetention.kt)。

## 独立复核与此次修正

[独立源码审计](agora-independent-audit.md) 先读取 Agora，再比较 `07277d4` 基线；报告保留修正前的问题和证据。该审计确认主要动画规格已相同，但背景合成、标题滚动、缓存范围、系统栏布局和首帧同步点仍有差异。

- 所有导航页面的窗口使用 edge-to-edge；主页、滚动页和表单各自处理 system bar / cutout / IME insets，外层遮罩和缩放覆盖整个窗口。
- `AgoraSettingsScaffold` 替代 AppBar / CollapsingToolbar 的第二滚动坐标；Column 的顶部空间与 RecyclerView 的首个 header item 随正文一起滚动，标题以同一坐标更新，不另开弹簧。长标题按原文从 33sp 自动适配至最低 20sp；RTL 镜像位移和标题锚点。
- scrim 关闭整个顶层浮层；页面返回仍逐层返回。

仍有明确边界：业务控件和导航宿主是 Views，未照搬整套 Compose 页面；840dp 宽屏目前限制内容宽度，未实现 Agora 的设置双栏和 250ms 分类 Crossfade；没有新增 Agora 的应用级 reduceMotion 开关。上述回归不能证明实机导航与参考视觉完全相同。

## 页面结构与性能

- 设置入口只创建分类导航。8 个分类各有独立布局，打开后只创建自己的控件；预览仅在相关分类创建。
- 自定义歌词、同步记忆、歌词源和候选列表使用 RecyclerView。滚动时复用 Material 行，数据解析与歌词摘要在 IO 线程准备。
- 歌词源搜索保留输入框；连续输入延迟 120ms 再过滤，取消前一次任务。新结果回到主线程时校验所选来源，避免过期页面覆盖。
- 编辑页先创建表单，再在 IO 线程读取已有记录，直接复用当前控件。实时校验延迟 120ms 合并输入，导入、验证和保存均在 IO 线程执行；重复保存与过期导入会被取消或拦截，导入过程中继续修改的内容不会被迟到的结果覆盖。
- 歌词源的新候选与历史记录在 IO 线程持久化；离开候选页面后，迟到的请求不覆盖其他歌曲的页面反馈。
- 主页缓存读取、自定义歌词解析与 LyricParser 运行在 IO 线程；切歌与返回主页都校验请求代次。相同歌词/偏移不重新构建歌词行。
- 主题切换不绘制全屏截图、不创建全屏 Bitmap；只对已布局的新内容做 200ms 透明度反馈，恢复滚动与焦点。
- 列表不放进 NestedScrollView，保证实际按可见区域回收；播放与预览在后台停止逐帧任务。

## 字体来源

`app/src/main/res/font/mioutfit_variable.ttf` 来自上述 Agora 版本同路径，保留原文件。Agora 仓库的许可副本位于 [agora-GPL-3.0.txt](licenses/agora-GPL-3.0.txt)。色阶使用 Google `@material/material-color-utilities` 0.3.0 的 SchemeTonalSpot 生成；生成步骤无需作为运行时依赖。

## 验证范围

Node 检查数据引擎、匹配记忆与 UI 边界。Robolectric 检查每个分类在浅深色下独立创建、设置入口不创建细节控件、导航、形状与播放/编辑操作；GitHub Actions 编译并生成 APK 和 UI 预览。

这些验证不能给出真机帧率。打开耗时、手势跟随、高刷新率和系统 WebView/网络获取等待，需要安装测试 APK 后测量。
