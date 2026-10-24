# Agora 风格的 Material 设计

参考源码：[newo-ether/Agora](https://github.com/newo-ether/Agora)，版本 `239eaa93a08412f79495d711ed3b05f495f0127e`。实现从歌词项目的 `main`（`7ae9772`）开始，没有合入 `feat/apple-material-ui` 的 Squircle、滑动分段或自定义按压组件。

## 设计来源与对应实现

| Agora 源码 | 歌词项目中的对应规则 |
| --- | --- |
| `ui/theme/Color.kt`、`Theme.kt` | 默认 FOREST 种子 `#2E7D32`，Tonal Spot、对比度 0；浅深色完整的 primary / secondary / tertiary、容器、描边、错误和反色角色 |
| `ui/theme/Type.kt` | 同一 MiOutfit 可变字体；正文 16sp、说明 13sp、列表标题 19sp、展开页标题 33sp、收起标题 22sp |
| `ui/settings/SettingsScreen.kt` | 24dp 内容边距；组标题为 primary 色；组内 2dp 间距，外角 24dp、内角 5dp；无堆叠阴影 |
| `ui/settings/SettingsScaffold.kt` | 154dp 展开标题区收缩到 64dp；标题随滚动收起；原生 CollapsingToolbarLayout 复用一套标题绘制 |
| `ui/settings/SettingsAnimations.kt` | 前后方向对应的 75% 横向移动与 0.94→1 缩放，300ms 淡入；阻尼 0.5、刚度 200 的弹簧曲线；页面进入时立即创建、交互，不等待动效结束 |
| `ui/components/SmoothBottomSheet.kt` | 面板弹簧阻尼 0.9、刚度 350；28dp 顶角、最大宽度 640dp、最大高度 94%、0.32 遮罩；Material 负责拖动、返回和点外部关闭 |
| `ui/components/AgoraOptionHighlight.kt` | 面板选项采用透明底、24dp 涟漪高亮，图标与文案对齐 |
| `ui/motion/AgoraMotionPolicy.kt` | 关闭系统动效时取消面板空间移动；窗口转场遵循系统动画设置 |

参考的设计规则以原生 Android Views 实现。歌词逐字绘制、播放时钟、同步偏移和翻译规则保留；播放控制栏使用相同的表面、字体和颜色角色。

## 页面结构与性能

- 设置入口只创建分类导航。8 个分类各有独立布局，打开后只创建自己的控件；预览仅在相关分类创建。
- 自定义歌词、同步记忆、歌词源和候选列表使用 RecyclerView。滚动时复用 Material 行，数据解析与歌词摘要在 IO 线程准备。
- 歌词源搜索保留输入框；连续输入延迟 120ms 再过滤，取消前一次任务。新结果回到主线程时校验所选来源，避免过期页面覆盖。
- 编辑页的实时校验延迟 120ms 合并输入，导入、验证和保存均在 IO 线程执行；重复保存与过期导入会被取消或拦截。
- 主页缓存读取、自定义歌词解析与 LyricParser 运行在 IO 线程；切歌与返回主页都校验请求代次。相同歌词/偏移不重新构建歌词行。
- 主题切换不绘制全屏截图、不创建全屏 Bitmap；只对已布局的新内容做 200ms 透明度反馈，恢复滚动与焦点。
- 列表不放进 NestedScrollView，保证实际按可见区域回收；播放与预览在后台停止逐帧任务。

## 字体来源

`app/src/main/res/font/mioutfit_variable.ttf` 来自上述 Agora 版本同路径，保留原文件。Agora 仓库的许可副本位于 [agora-GPL-3.0.txt](licenses/agora-GPL-3.0.txt)。色阶使用 Google `@material/material-color-utilities` 0.3.0 的 SchemeTonalSpot 生成；生成步骤无需作为运行时依赖。

## 验证范围

Node 检查数据引擎、匹配记忆与 UI 边界。Robolectric 检查每个分类在浅深色下独立创建、设置入口不创建细节控件、导航、形状与播放/编辑操作；GitHub Actions 编译并生成 APK 和 UI 预览。

这些验证不能给出真机帧率。打开耗时、手势跟随、高刷新率和系统 WebView/网络获取等待，需要安装测试 APK 后测量。
