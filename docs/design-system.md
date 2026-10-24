# Agora Compose 设计来源

应用页面使用 Jetpack Compose，参考 [Agora 239eaa93](https://github.com/newo-ether/Agora/tree/239eaa93a08412f79495d711ed3b05f495f0127e)。页面不再遵循旧 View/XML/Fragment 的布局和动画适配规则。

| 设计内容 | 运行时源码 |
| --- | --- |
| FOREST / Tonal Spot 的完整 Material 色彩角色 | `ui/agora/Color.kt`、`Theme.kt` |
| MiOutfit 字体及原文 Typography | `ui/agora/Type.kt` |
| 24dp 外圆角、5dp 内圆角、2dp 组内间距 | `ui/agora/SettingsGroups.kt` |
| 单个滚动标题，154dp 标题空间、66dp 竖向行程 | `ui/agora/SettingsScaffold.kt` |
| 首页上方覆盖层与独立不透明 Surface | `ui/agora/SettingsOverlayHost.kt` |
| 可中断的当前／离场页面动画与输入隔离 | `ui/agora/SettingsAnimations.kt` |
| 会话内按导航路径保留 scroll owner | `ui/agora/SettingsScrollRetention.kt` |
| 840dp 宽屏阈值、400dp 左栏、250ms 分类交叉淡入淡出 | `ComposeNavigation.kt`，复用原文双栏几何与行组件 |
| 改变实际高度的可拖动面板；0/.45/.94 锚点、.9/350 弹簧、随高度变化的 dim | `ui/agora/SmoothBottomSheet.kt` |
| 24dp 菜单与选项高亮 | `ui/agora/AgoraDropdownMenu.kt` |
| 应用减少动效设置与三项系统动画比例观察 | `ui/agora/AgoraMotionPolicy.kt` |

自定义主色仍通过原文的 Tonal Spot 算法生成完整颜色角色。主题由 Compose 根节点观察偏好和系统变化，无截图过渡或 Activity 重建。首页歌词使用 LazyColumn、monotonic LyricClock 和逐字绘制；设置、管理、编辑及隐私页面使用原文 Scaffold 和 Material3 控件。

面板关闭时额外同步清零窗口 dim，并禁用独立的平台窗口动画，避免弹窗销毁取消最后一帧遮罩更新。面板的运动曲线、拖动与 nested scroll 保持原组件逻辑。

原组件的 GPL-3.0 许可保留在 `ui/agora/LICENSE`。架构及验证范围见 [Compose 迁移说明](agora-compose-migration.md)。
