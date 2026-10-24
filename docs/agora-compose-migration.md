# Agora Compose 页面迁移

参考仓库：https://github.com/newo-ether/Agora
参考提交：239eaa93a08412f79495d711ed3b05f495f0127e

本次在 main 将应用页面迁为 Jetpack Compose。旧 NativePage、View 导航宿主、XML 页面、标题适配器和自行驱动的页面动画已移除。MainActivity 保留 MediaSession、歌词检索、缓存、翻译、导出及权限业务；页面由 Compose 状态驱动。系统浮窗继续由 WindowManager 服务提供，其隐藏 WebView 负责现有歌词匹配引擎，不作为应用页面。

## 原源码复用

`app/src/main/kotlin/com/luoh/music/lrc/ui/agora/` 保留 Agora 的设置外层宿主、内页过渡、滚动恢复、单标题 scaffold、分组行、SmoothBottomSheet、菜单、配色与字体源码；调整包名、资源和应用业务接线；弹窗关闭时额外同步清零 dim，并禁用独立的平台窗口动画，避免最后一帧更新被销毁取消而残留遮罩。该目录包含上游 GPL-3.0 许可副本。主题额外支持本应用已有的自定义色种，仍使用原件的 Tonal Spot 算法生成完整 ColorScheme。

页面入口统一使用 ComponentActivity.setContent。首页播放器、权限页、逐字歌词、更多面板、八类设置、翻译/API 配置、同步记忆、歌词来源及版本、自定义歌词列表与编辑、隐私页全部使用 Compose。内部导航无需切换 Activity，外部系统入口映射为同一目的地模型。

## 交互和性能

- 外层采用原文 SettingsOverlayHost，首页持续留在 composition 中；页面由不透明 Surface 承载。
- 内页采用原文 GuardedAnimatedContent，当前目的地可接受输入，退出页面屏蔽输入，连续导航由 Compose 取消和接续动画。
- 滚动标题沿原文 66dp 轨迹，scroll owner 仅在设置会话内保留；退出页面不覆盖返回页的位置。
- 宽屏采用原件 840dp 阈值、400dp 左栏、右栏 16dp 边距与 840dp 内容上限。分类切换 Crossfade 250ms，子页单独进入过渡宿主。
- 更多菜单采用原文 SmoothBottomSheet，不使用 ModalBottomSheet 替代。锚点为 0/.45/.94，弹簧为 .9/350，窗口 dim 随面板高度同步变化。
- 网络、文件解码、歌词解析和动态列表数据处理放在 IO 上。退出的管理/编辑页取消业务协程，避免继续发布结果。主页逐字进度由 monotonic LyricClock 驱动，帧更新仅触发活跃歌词绘制；页面遮挡或后台时停止帧循环。

## 验证边界

CI 编译和 Robolectric Compose 交互测试验证源码接入、导航输入、滚动恢复及业务回归。旧 View UI 测试已替换，歌词/浮窗引擎的有效回归仍保留。没有设备侧帧时间或启动耗时测量，不以测试通过声称与 Agora 的主观流畅度完全一致。

## 补充翻译与高刷新率

补充翻译通过 Material3 选择弹窗切换方式，对应配置直接显示在本页下方。API 输入框拆为独立 LazyColumn 项，语言包管理器仅在选择离线方式后初始化；配置元数据和语言名称准备放在 IO 上。歌词来源一次发布已准备好的行快照，自定义歌词列表只保存轻量行信息，避免将完整歌词负载带入界面比较与重组。

主窗口和原文弹层使用 Compose `preferredFrameRate(FrameRateCategory.High)`，在支持 Android 自动刷新率的设备上请求适合交互动画的高刷新率。没有锁定 60Hz 或按固定帧数计时；动画和逐字绘制沿用系统帧时钟，因而支持系统当前的 90/120Hz 等显示模式。此请求不是强制切换显示模式；设备高刷开关、省电策略和平台能力仍决定实际刷新率。120Hz 每帧预算约 8.33ms，未取得真机帧时间数据，不能声称始终稳定 120fps。
