# Material 原生界面

本次重构针对所有用户可见界面，不是仅替换设置页的几个按钮，也不是迁移到 Flutter。

## 组件与样式

- 应用主题：Material 3 DayNight，映射到原有黑白灰调色板，不启用动态彩色主题。
- 按钮、图标、分段选择：MaterialButton / MaterialButtonToggleGroup。
- 导航、弹窗和底部菜单：MaterialToolbar / MaterialAlertDialogBuilder / BottomSheetDialog。
- 设置开关、字号和颜色滑杆：MaterialSwitch / Slider。
- 表单和分组：TextInputLayout + TextInputEditText / MaterialCardView。
- 歌词、说明文字和封面：MaterialTextView（含原生逐字绘制）/ ShapeableImageView。
- Android 的布局容器和滚动容器继续用于排版；Material 没有现成的逐字歌词组件，因此歌词排版使用原生自定义文本视图。

中控台歌词图标与右侧更多图标使用相同的图标尺寸、48dp 点击区域和垂直对齐。图标无边框，未开启时背景透明，已开启时只有低透明度的单色底色。开关状态以服务回报为准，不在请求授权时提前亮起。

颜色选择器把等分布局与圆形外观分开：外层负责分配空间，内层固定等宽等高。开关使用 Material 的圆形滑块；轨道仍是胶囊形。

## 歌词与生命周期

主页不再用 WebView 绘制歌词或中控台。原生歌词模型、解析器、播放时钟和文本视图负责 LRC、逐字、翻译以及行切换；播放控制继续调用 Android MediaSession。歌词偏移只影响歌词，不改变播放进度条的音频时间。

悬浮窗的可见歌词与关闭、锁定控件也使用原生视图。WindowManager、通知权限、悬浮窗权限、拖动与位置记忆仍由原服务负责。

**本次没有重写全部歌词数据引擎。** 悬浮窗保留隐藏 WebView 承载既有匹配、候选历史、localStorage 缓存和翻译调度，以免 UI 重构改变用户保存的歌词版本。它只传递歌词数据，不参与可见界面的排版或动画；原生定期驱动数据引擎，不依赖隐藏网页的 requestAnimationFrame。应用因此仍需要 Android System WebView。

这一区分意味着“可见 UI 全部原生”，不意味着“仓库中的 JavaScript 已全部删除”。以后如要完全移除 WebView，应另行迁移数据引擎并验证缓存和匹配行为，不能直接删除旧引擎。

## 验证

- Node 回归测试继续覆盖匹配记忆、过期响应和自定义歌词优先级。
- JVM 单元测试覆盖原生歌词解析、播放时钟与上下文数据规则。
- Robolectric 检查 Material 布局可创建、浅深色主题、小屏等宽圆形，以及图标按钮的等大、无边框和半透明选中状态。
- 编译与原生测试在 GitHub Actions 中运行；不在开发服务器运行 Gradle。

自动化检查不能替代真机视觉验收。安装测试 APK 后，仍需检查播放器切歌／拖动进度、授权取消、后台悬浮窗、长句逐字、颜色选择和系统字体缩放。
