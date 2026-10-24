# Agora 导航独立源码审计

审计日期：2026-10-24。先阅读 Agora，再阅读目标实现；未查看图片，未使用现有测试通过结果证明视觉一致。本报告描述的是下面两个固定提交的实现，后续修复应与该基线区分。

- 参考：[newo-ether/Agora，239eaa93](https://github.com/newo-ether/Agora/tree/239eaa93a08412f79495d711ed3b05f495f0127e)，本地 `/data/data/com.termux/files/home/projects/.reference-agora`。
- 目标：lyrics-android，`07277d44508e37c4a48f1591ed4705c8483842da`。
- 检查范围：主页打开设置与其他顶层页面、分类与子页、返回与连续操作、容器与变换、帧驱动、动画取消、减弱动态效果、触摸、页面销毁与滚动恢复。

## 结论与优先级

目标已经使用与参考相同的 Compose `Animatable`、命名弹簧常量、主要位移/缩放/透明度规格，以及可中断的 target/outgoing 双页面。仅凭“使用 FrameLayout”不能断言无法实现相同效果；也不能凭“使用 animation-core”断言渲染、布局、首帧和交互已经完全一致。

目前最有力、应优先修复的差异是：外层背景缺失导致内页切换透出主页；标题与列表并非参考的单滚动几何；滚动缓存仅附着在栈条目，不能保留同一设置会话内重复进入分类的位置。整个窗口是否采用 edge-to-edge 也应明确对齐。首帧同步确有结构差异，但现有证据不足以量化它在实机上增加多少延迟。

这些确定性差异可以先在现有业务 Views 上修正。是否换用原始 Compose 宿主，应由修复后的同设备帧与输入证据决定，不能由尚未验证的“View 天生不等效”推论决定。

## 参考的真实导航路径

### 主页到设置、任务和远程

[MainActivity.kt](https://github.com/newo-ether/Agora/blob/239eaa93a08412f79495d711ed3b05f495f0127e/app/src/main/java/com/newoether/agora/MainActivity.kt#L456) 在同一 `Box` 中始终保留 `ChatApp`，随后放置远程、设置和任务 overlay。主页没有参与设置进入的退场位移或缩放。设置和任务直接使用 `SettingsOverlayHost`（538、570 行）；远程的 `RemoteOverlay` 在 `ui/remote/RemoteScreen.kt:98` 也使用它。

真实设置入口是 `ChatDrawerContent.kt:776` 的按钮：先清焦点、调用 `onOpenSettings()`，然后异步关闭 drawer（779–780 行）。任务与远程同样在进入新 overlay 的同时关闭 drawer（315–316、332–333 行）。因此参考真实入口的下层画面可能还包含 drawer 关闭过程，这属于入口环境，不能把单独播放 overlay 的录像直接当作全部导航效果。

[SettingsOverlayHost.kt](https://github.com/newo-ether/Agora/blob/239eaa93a08412f79495d711ed3b05f495f0127e/app/src/main/java/com/newoether/agora/SettingsOverlayHost.kt#L71) 的进入与退出不同：

| 属性 | 进入 | 退出 |
| --- | --- | --- |
| 页面横向起点/终点 | `+0.25W → 0` | 当前值 `→ +1W` |
| 页面缩放 | `.92 → 1` | 当前值 `→ .94` |
| 页面透明度 | `0 → 1`，300 ms tween | 当前值 `→ 0`，400 ms FastOutSlowIn |
| 遮罩透明度 | `0 → .45`，300 ms tween，延迟 50 ms | 当前值 `→ 0`，400 ms FastOutSlowIn |
| 位移与缩放 | LowBouncy / StiffnessLow / threshold `.001` | 400 ms FastOutSlowIn |

四个动画并行，`joinAll()` 后才完成（89–184 行）。关闭开始后内容仍保留用于绘制，但额外覆盖层消费触摸（231–237 行）。`renderOverlay` 仅在全部退出动画结束后设为 false。`TopLevelPresentationState` 直到退出完成才释放主页呈现所有权（`MainActivity.kt:541`；`TopLevelPresentation.kt:42`），较旧 overlay 的退出回调不能覆盖较新的所有权。

### 分类与子页

[SettingsScreen.kt:317](https://github.com/newo-ether/Agora/blob/239eaa93a08412f79495d711ed3b05f495f0127e/app/src/main/java/com/newoether/agora/ui/settings/SettingsScreen.kt#L317) 保留 `selectedCategory`；窄屏在 null 和分类之间使用 `GuardedAnimatedContent`（348 行）。分类子页本身再次使用同一个宿主，例如 provider → provider detail → editor（`SettingsProviderPage.kt:44`，`SettingsProviderDetailPage.kt:68`）、shell → sandbox/device editor（`SettingsShellPage.kt:66,80`）。这是一组嵌套导航边界，而不是所有路由都由一个外层 slide 取代。

[SettingsAnimations.kt:56](https://github.com/newo-ether/Agora/blob/239eaa93a08412f79495d711ed3b05f495f0127e/app/src/main/java/com/newoether/agora/ui/settings/SettingsAnimations.kt#L56) 的原文机制：

- 初次组成的 target 稳定显示，不播放内页入场（65–75 行）。后续新 target 从 `±.75W`、alpha 0、scale .94 进入；outgoing 从独立 exit progress 0 向相反方向 `.75W` 退出。
- 新 target 的位移与缩放使用弹簧，alpha 使用 300 ms tween；outgoing 的位移、alpha、scale 全部使用弹簧（101–109 行）。不能把双方透明度都改成 tween。
- 路由改变取消前一 `LaunchedEffect`。先删除更旧 outgoing，再把当前 target 改为 outgoing，新建唯一 target（81–99 行）。保留当前页面已达到的 alpha/scale；其位移改由独立 exit progress 驱动。这种中断本身可能重定位尚未完成的 enter 位移，不能以“视觉应绝对位置连续”擅自改写参考。
- target 位于 zIndex 1，outgoing 位于 0；outgoing 消费触摸。过渡期间另有全尺寸 zIndex .5 的输入盾，封住新页面未覆盖区域（143–178 行）。新页面在动画未结束时即可接受输入。
- 动画结束删除 outgoing 并把 target 的值归一。旧页面组合随后销毁。参考并没有永久保留所有页面。

宽度至少 840 dp 时走另一条路径：400 dp 导航栏常驻，右侧分类采用 250 ms `Crossfade`，内容容器最大宽度 840 dp 并裁剪；系统返回直接关闭设置（`SettingsTwoPane.kt:48,71,89,97`；`SettingsScreen.kt:334–346`）。目标基线没有该宽屏导航分支。

## 已确定的剩余差异

### 1. 外层不透明背景缺失，内页切换会露出主页

参考 `SettingsOverlayHost.kt:224–229` 的 `Surface(background)` 始终覆盖整个外层页面。内页都位移、缩小或透明时，底层仍然是设置背景。目标 `AgoraNavigationOverlay.kt:24` 的 `AgoraPageSurface` 只挂载 `host`，没有背景；`AgoraPageSurface` 类本身也没有背景（`AgoraPageMotion.kt:21–25`）。内页背景随着该内页 alpha 与 transform 一起消失，下面就是遮罩和主页。

这是确定的合成结构差异，不需要依赖图片判断。对于背景区域，设页 alpha 为 a，已稳定的遮罩为 .45：目标没有其他覆盖页的位置，主页贡献为 `(1-a) × .55`；参考由外层背景补齐，主页贡献为 0。

独立 Python 解析计算使用相同规格、时长倍率 1、已稳定的外层、零初速，取内页过渡开始后 150 ms：弹簧 progress 约 .738239，outgoing alpha 约 .261761，incoming tween alpha 约 .775561。计入中心缩放后，outgoing 覆盖约 `[-.5315W,.4242W]`，incoming 覆盖约 `[.2042W,1.1885W]`。在无文字的 `x=.1W` 区域，仅 outgoing 覆盖，目标主页贡献约 **40.6%**；`x=.5W` 仅 incoming 覆盖，约 **12.3%**；参考这两个位置均由外层背景补齐。这是理想背景合成计算，未声称已测得实机像素或实际帧恰好落在 150 ms。

优先修复：在 outer surface 上设置会随主题更新的、不透明的当前背景；让这个背景随外层进入/退出，但不随内部路由过渡消失。

### 2. 标题和列表的滚动几何不同

参考 `SettingsScaffold.kt:58–72` 的 bar=64 dp、header room=90 dp、bottom inset=70 dp、dock top=18 dp，因此标题 travel 是 **66 dp**，并非 90 dp。整个列表拥有滚动：header spacer 与行一起离开；标题只是置于列表上方的单个 `Text`。其 y 与滚动线性对应，scale/x 使用 `CubicBezierEasing(.2,0,.5,1)`（130–173 行），不另启动标题动画。Column 的 fraction 来自 scroll value；LazyColumn 的 fraction 来自 header item index/offset（199–201、267–271 行）。

目标 `NativeUi.kt:163–193` 和 `res/layout/activity_settings.xml:3–9` 使用 `AppBarLayout + CollapsingToolbarLayout + NestedScrollView/RecyclerView`。154 dp header、64 dp minimum 产生 **90 dp AppBar 折叠范围**；AppBar offset 与正文 scroll 是不同 owner。Material 1.11.0 的 `CollapsingToolbarLayout.java:213` 默认设置文字尺寸 `DecelerateInterpolator`，`CollapsingTextHelper.java:625–628,1020` 按折叠 fraction 计算文字位置/尺寸；它不是参考的自定义 scale/x 曲线。

可确认的数字是：参考滚动 66 dp 时标题已完全 dock；目标 AppBar 折叠 66 dp 时 fraction 仅 66/90=.7333。假设标题能以 33 sp 显示且默认 Decelerate 取 factor 1，参考已到 22 sp，Material 约 22.78 sp。两者正文滚动坐标还受不同 nested-scroll 分配影响，不能把相同 `NestedScrollView.scrollY` 当作同一个总滚动进度。

参考还针对长标题从 33 sp 自动减至最低 20 sp（`SettingsScaffold.kt:113–128`），正文限制宽度 840 dp；目标此处没有同样的测量和上限。

优先修复：改为正文拥有唯一滚动进度、包含 154 dp header 空间；固定 64 dp bar，单个 title 用 `min(scroll/66dp,1)` 驱动 y，使用原曲线驱动 x 与 scale，起点 x=24 dp、终点 x=70 dp，y 从 status inset+84 dp 到 status inset+18 dp。业务控件仍可使用 Views；若保留 Material AppBar，应明确它只能近似参考几何。

### 3. 同一设置会话的滚动保留范围不同

参考 `SettingsScrollRetention.kt:17–59` 仅保留 scroll owner，不保留页面组合和编辑草稿。缓存存在整个 `SettingsScreen` 会话（`SettingsScreen.kt:331`），按嵌套 `contentKey` 路径区分分类/子页（`SettingsAnimations.kt:161–164`），至多 64 项。新组合从旧 scroll 值创建独立 owner；outgoing 停止发布到缓存。

目标 `NativeNavigator.kt:60,75` 把 scroll 存到当前栈条目的 Bundle；`back()` 删除该条目（76 行），之后再次 `open()` 创建全新 Bundle（62 行）。因此“进入分类 A → 滚动 → 返回设置主页 → 再进入 A”，A 的 scroll 失去旧入口，而参考会恢复 A。父页被压栈、随后返回的简单路径已有恢复（`NativePage.kt:54–74`），但不能据此推断上述重新进入分类也已一致。

目标在点击时捕获 scroll；参考缓存活动 owner，最后状态随 owner 更新。若离开时还有 fling，二者采样时机可能不同，实机影响尚未验证。目标以 View 子节点索引路径保存状态；参考以稳定导航身份路径保存，两者在动态页面结构变化时的稳定性也不同。

优先修复：建立 overlay 会话级、最多 64 项的 scroll-only 缓存，以分类与稳定子路由身份路径为键。每次进入创建新页面，读取已有位置；离开/outgoing 不再覆盖活动 owner 的缓存。关闭 overlay 清空该会话。不要通过永久保留 Fragment 或保存整页草稿达到滚动恢复。

### 4. 全屏容器、系统栏与宽屏布局不同

参考 `MainActivity.kt:122–125` 显式 `enableEdgeToEdge()` 并禁用导航栏对比遮罩；各 scaffold 自己添加 status/nav/IME inset。外层整个 `fillMaxSize()` 接受 transform 和 scrim。

目标 `MainActivity.kt:157` 把 overlay 添加到 `android.R.id.content`，代码未设置 edge-to-edge；当前 targetSdk=34，主题设置有颜色的 status/navigation bar（`app/build.gradle.kts:23`，`res/values/themes.xml:6–7`）。依照正常窗口布局，系统栏由窗口管理，未被包含在这套 View transform/scrim 中。可以确认设置范围不同；具体像素高度与用户系统配置应由 window insets 实测，不应猜定固定 24/48 dp。

因此，整个页面缩放的中心与可见高度会不同，状态栏背景也不会像参考的页面内容一起被缩放/遮罩覆盖。这不是横向位移小数或阻尼能修复的问题。需要选择一致的窗口布局，并由各页面明确处理 insets；不能只打开 edge-to-edge 而不处理业务控件的 inset。

参考宽屏双栏在前文说明；目标全宽、单栏路由栈也不是同一个布局。宽屏差异是否纳入当前产品要求需由实际支持范围决定。

### 5. 减弱动态效果的响应方式不同

参考 `AgoraMotionPolicy.kt:46–105` 把应用 reduceMotion 与系统 animator/transition/window 中任一倍率为 0 合并；ContentObserver 更新 CompositionLocal。应用 reduceMotion 保留淡入淡出、去除空间变换，不是强制所有时长为 0。

目标 `AgoraPageMotion.kt:14–18` 仅在新过渡开始时读系统三项；每个 slot 把 spatial 捕获成不可变值（29 行）。没有对应应用全局 reduceMotion，也没有让现存页面变换随 policy 改变。`AgoraTransitionRuntime.kt:60–69` 的 observer 只更新 animator 时长倍率，不等同于空间能力 policy。

参考外层 `LaunchedEffect(visible)` 不因 policy 改变重播入场，但绘制时读取新 policy、可以立即去掉 offset/scale（`SettingsOverlayHost.kt:87,192,219`）。内层 effect 还以 policy 为 key；policy 在过渡中改变会取消效果，而相同 target 不进入新切换分支（`SettingsAnimations.kt:81–85`）。不要自行声称原文此情况下会自动重新启动并完成 crossfade。

系统 animator 倍率 0 时，Compose 的 MotionDurationScale 同样可能让淡入淡出直接结束；参考的“保留 300 ms crossfade”描述主要适用于应用 reduceMotion 或另外两项系统动画被关闭、animator 仍非零的情形。

## 普通过渡中基本等效的部分

| 机制 | 参考 | 目标基线 | 判断 |
| --- | --- | --- | --- |
| 物理规格 | LowBouncy、StiffnessLow、threshold .001 | `AgoraTransitionRuntime.kt:31–35` 同名常量 | 参数相同；不应继续猜阻尼 |
| 外层规格 | .25W/.92、四路动画、400 ms 退出 | `AgoraNavigationOverlay.kt:35–85` | 主要数值及 easing 相同 |
| 内层规格 | 双 slot、±.75W/.94、incoming tween/outgoing spring | `AgoraPageMotion.kt:29–103` | 主要参数相同 |
| 连续切换 | 丢弃更旧 outgoing，当前 target 改 outgoing | 49–63 行取消旧 Job并执行同样角色替换 | 普通不同路由操作近似等效 |
| 中断值与速度 | 取消后保留 value，Animatable 重置速度 | 同一 Compose Animatable engine | 不能把“零初速退出”误判成移植缺陷 |
| 输入 | target 在 outgoing/input shield 上方且可交互 | surface/shield elevation 2/1/0，outgoing 拦截 | 目的相同；嵌套输入与绘制顺序仍要实测 |
| 主页保留 | ChatApp 常驻，退出完成释放 owner | `NativeNavigator.kt:79–87` 退出完成移除 overlay，主页常驻 | 普通进入与返回的持有范围相同 |
| 页面销毁 | 完成删除旧组合 | discard 移除旧 View/Fragment | 均没有永久保留所有页面 |

额外边界：参考点击外层 scrim 直接关闭整个设置（`MainActivity.kt:540`）。目标 scrim 调用 `back()`（`NativeNavigator.kt:95`），若内部栈已有子页则只退一级。该差异可在“外层尚在入场、已进入内页且 scrim 仍暴露”时发生；稳定全屏覆盖时 scrim 没有直接可点区域。相同状态重复赋值在参考没有切换，但目标每次 `open()` 都压入条目；是否在真实触摸路径中能形成同路由重复操作需按实际坐标验证。

## 首帧、帧时钟与证据不足的假设

### 可确认的结构

参考 host 用 `LaunchedEffect` 与 Compose `Animatable.animateTo`，没有额外 preDraw 等待。target 外层 `AgoraNavigationOverlay.kt:46`、内层 `AgoraPageMotion.kt:76` 等待 `awaitAgoraDraw()`；该函数是 `doOnPreDraw`，不是“已经显示了一帧”（`AgoraTransitionRuntime.kt:100–102`）。等待结束后，第一次 animateTo 仍需等帧时钟。

已独立阅读 Google Maven 的 Compose 1.6.1 source：`SuspendAnimation.kt:237–252` 在未指定起始时间时第一次 `withFrameNanos` 建立起点，首个动画取样是 t=0；`Animatable.kt:331–368` 取消时清零 velocity 与 lastFrameTime。目标 `BroadcastFrameClock.sendFrame` 向一组等待者广播相同 Choreographer timestamp，通常保持六路动画同步；每个 render() 设置 View 属性，真实绘制仍在 traversal 统一进行，不会因为六次 render() 就必然显示六个半更新画面。

Compose UI 的 `AndroidUiDispatcher` 在 Choreographer callback 先完成协程调度，再发布 frame；`AndroidUiFrameClock` 对应排队逻辑与 target 直接使用 `Dispatchers.Main.immediate + BroadcastFrameClock` 不同。目标内层/外层各有 runtime，参考同一 composition 使用 Compose 所提供的帧环境。

### 未经验证，不能作为视觉结论

- 尚未测量“点击 → 初始页面状态真正绘制 → 第一次非零动画进度”的真实时间。额外 preDraw 是确定同步点，是否相对参考增加一帧，取决于 effect/回调注册所在的 Android 帧阶段。不能直接宣称已证实多 16 ms，或移除它一定治好顿挫。
- `Modifier.offset(IntOffset)` 是布局放置，View `translationX` 是变换；在 LTR、全尺寸页面、中心缩放、相同 clip 和 parent 约束下，两者可产生相同屏幕位置与触摸坐标。仅有 API 名称差异不是显著视觉差异的证据。RTL 下参考 relative placement 与物理 translation 的关系应单独验证。
- 参考 zIndex 不产生阴影，目标 elevation 可能产生阴影，但当前透明 page surface 没有背景 outline，尚未证明它真的绘制可见阴影。若补不透明背景，需同时确保排序不意外引入实体阴影。
- 目标 `clipChildren=true`；参考过渡 Box 未显式 clip，宽屏右侧 pane 显式 clip。全屏单栏通常受到窗口边界限制；没有证明 clip 差异本身导致当前手机上的主要异常。对嵌套 pane、缩放超调和非全屏容器要单独验证。
- 目标依赖 Compose 1.6.1（`app/build.gradle.kts:93–95`），参考使用 BOM 2026.05.01（`gradle/libs.versions.toml:7`）。已确认版本不同；未证明这造成命名常量或弹簧轨迹改变，不能把版本差异当作原因结论。
- Fragment inflate、`commitNow()`、正文加载可能影响首帧耗时，但没有本次独立实机 profile，不能归因卡顿。Reference Compose 的内容创建也需要 UI 工作。

## 推荐的可复核下一步

1. 先固定外层不透明背景，并检查排序只影响绘制/输入而不添加阴影。用不同高对比主页背景，在内页 alpha 已小于 1 时验证设置背景不透主页。
2. 用原文单 scroll 标题几何替代 AppBar/NestedScroll 两个 offset；记录正文 offset=0/33/66/90 dp 时标题 x/y/scale 和第一行位置。正文 finger tracking 与标题 fraction 要使用同一个总滚动坐标。
3. 引入会话级 scroll-only 缓存，核对“进入 A → 滚动 → 返回主页 → 重新进入 A”和“子页 → 返回”的区别；同时核对快速返回时各组合/页面使用独立 scroll owner，编辑草稿不被无意保留。
4. 对齐窗口和 insets，再记录整个 overlay 的宽高与 scale pivot，验证系统栏范围。宽屏要按支持范围明确是否采用参考双栏。
5. 仪器化点击、mount、preDraw、Choreographer timestamp、首个非零 value 与退出清理时刻；只在证据显示额外同步造成延迟时调整首帧机制。连续操作至少覆盖外层未完成时开子页、内页未完成时返回、返回未完成时进另一分类、外层退出时再点入口。

若希望直接保证 Compose 的 offset/graphicsLayer、effect/frame 环境与输入 shield 语义，可保留业务 Views 并用 `AndroidView` 放进原始 Compose 壳，这是合理备选。但仅换壳不会自动修复正文标题、session scroll、窗口 insets 或错误背景；当前证据支持先修上述明确差异，而不是要求先做全 Compose 迁移。

补充源码依据：Material 1.11.0 [CollapsingToolbarLayout.java](https://github.com/material-components/material-components-android/blob/1.11.0/lib/java/com/google/android/material/appbar/CollapsingToolbarLayout.java#L213)、[CollapsingTextHelper.java](https://github.com/material-components/material-components-android/blob/1.11.0/lib/java/com/google/android/material/internal/CollapsingTextHelper.java#L602)；Compose 1.6.1 [animation-core sources](https://dl.google.com/dl/android/maven2/androidx/compose/animation/animation-core/1.6.1/animation-core-1.6.1-sources.jar)、[ui-android sources](https://dl.google.com/dl/android/maven2/androidx/compose/ui/ui-android/1.6.1/ui-android-1.6.1-sources.jar)。独立下载源文件放在工作区外 `.cache/lyrics-agora-independent-audit`。
