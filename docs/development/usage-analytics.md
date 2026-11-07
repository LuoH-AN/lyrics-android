# 使用情况统计（Umami）

应用通过 Umami HTTP 接口向现有网站项目上报。没有新增管理网站，也不在 APK 内嵌管理员密码或读取数据的令牌。

- 后台：<https://umi.luoh.org>
- Website ID：`c8a3563b-bf82-40c0-8352-de5438f4c653`
- 网站名称：桌面歌词 Android
- 统计域名：`lyrics-android.local`，只作为事件 hostname，不需要 DNS 解析。

默认开启，首次打开显示一次简短说明，不弹出授权对话框。可在「设置 → 关于应用 → 使用情况统计」或隐私说明页关闭，升级和重启保留已有的关闭选择。仅正式包 `com.luoh.music.lrc` 上报，`.dev` 包不上报。

## 直接在现有 Umami 查看

进入网站项目并选择时间范围。概览的 `/app` 与 `/overlay` 是应用前台和悬浮歌词的启动页面。新增统计在「事件」及「事件数据」中查看，不需要更换 Website ID 或再添加网站。不同 Umami 版本的入口名称可能略有区别。自定义报表、漏斗需要在 Umami 界面选择对应事件；应用不会替管理员创建报表。

| 想了解的情况 | 查看方式 |
| --- | --- |
| 活跃趋势、前台与悬浮窗使用 | 概览的访客、访问量，结合 `app_start`、`overlay_start`；时长查看 `usage_time` 的 `seconds` 数值总和 |
| 大家常用什么 | `player_used.player` 查看播放器；`feature_use.feature` 查看功能；`screen_view.screen` 查看进入的设置页面；`translation_setting.mode` 查看翻译模式 |
| 歌词查找成功率 | 比较 `lyrics_lookup_success`、`lyrics_lookup_not_found`、`lyrics_lookup_error`；成功率为 success / (success + not_found + error) |
| 翻译成功率 | 比较 `translation_success`、`translation_error`；成功率为 success / (success + error)，取消和不需补译的任务不进入分母 |
| 出问题的歌词源、翻译模式 | `lyrics_provider_result` 按 `source`、`result`、`failure` 查看；`translation_result` 按 `mode`、`result`、`failure` 查看 |
| 用户卡在哪个使用步骤 | 查看下方 `onboarding_*` 事件，按 `cohort=new_install` 分析新版新安装；权限是否开启看 `permission_state` |
| 哪个版本或机型崩溃 | `app_crash` 按 `app_version`、`device_brand`、`device_model`、`exception_type`、`fingerprint` 查看 |

成功率公式使用同一时间范围和版本。独立结果事件是为了便于现有 Umami 直接对比，和 `*_result` 记录的是同一次操作，不能相加。数值求和或公式展示取决于已安装 Umami 的报表功能；若界面不支持，可从 Umami 导出对应事件数据计算。

## 事件定义

| 事件 | 含义 | 主要属性 |
| --- | --- | --- |
| `app_start` / `app_stop` | 进入、离开前台，包含设置页；短暂活动切换合并 | 公共属性 |
| `overlay_start` / `overlay_stop` | 悬浮窗口成功创建、服务关闭 | 公共属性 |
| `usage_time` | 每分钟或离开时累计亮屏使用时长 | `surface=app/overlay`、`seconds` |
| `player_used` | 正在播放的播放器类别；同一随机安装标识、每个 UTC 日期每类最多一条，本机去重跨重启保留 | `player`，未识别的统一为 `other` |
| `lyrics_lookup_result` | 一次联网歌词查找的最终结果，不包含本地缓存命中 | `source`、`result`、`duration_ms`、`failure` |
| `lyrics_provider_result` | 某个歌词源的尝试，一次查找可以尝试多个源 | `source`、`result`、`duration_ms`、`failure` |
| `lyrics_lookup_success/not_found/error` | 最终查找结果的独立计数事件 | `source`、`failure` |
| `translation_result` | 一次补译任务的结果，含译文缓存返回 | `mode=offline/api`、`result`、`duration_ms`、`translated_lines`、`failure` |
| `translation_success/error` | 补译成功、失败的独立计数事件 | `mode`、`failure` |
| `permission_open` | 用户打开某项系统授权页面，不代表已经授权 | `permission=notification/overlay` |
| `permission_state` | 每进程首次观察与后续观察到的变化 | `permission`、`granted`、`observation=initial/changed` |
| `feature_use` | 使用主页播放控制、歌词重试/导出、主界面或设置中的悬浮窗开关、添加快捷开关或点击系统快捷开关 | `feature` |
| `screen_view` | 打开设置分类、翻译、API 配置、偏移、来源、自定义/缓存歌词及编辑、隐私页面 | `screen`，只上传固定页面名 |
| `translation_setting` | 前台恢复、返回主页或悬浮服务启动时读取翻译模式；同一进程相同配置不重复 | `mode=off/offline/api`、`display=original/translated/bilingual` |
| `lrc_export_result` | 选择目标文件后写入是否成功 | `result=success/error` |
| `app_crash` | 上次 Java/Kotlin 未捕获异常，重启后补传 | 见下方崩溃诊断 |

公共属性：`app_version`、`android_api`、`android_version`、`device_brand`、`device_model`、`analytics_schema=2`。系统语言通过请求的 `language` 字段提供。品牌型号来自 Android 公开 Build 字段，不读取设备唯一硬件标识。

`feature` 的固定取值为 `overlay_start`、`overlay_stop`、`seek`、`play_pause`、`previous`、`next`、`retry_lyrics`、`export_lrc`、`add_tile`、`tile_toggle`。功能事件表示操作触发，实际悬浮窗口成功显示应看 `overlay_start` 事件。

`result` 为 `success`、`not_found`、`error`、`cancelled` 或 `skipped`。歌词源失败不一定导致最终查找失败，应通过 `lyrics_lookup_result` 观察整体结果。`failure` 只有固定分类，例如 `timeout`、`network`、`model_missing`、`configuration`、`authentication`、`rate_limit`，不上传异常原文。

## 首次使用步骤

每个随机安装标识只记录一次各步骤，状态写入不参与备份的私有存储，重启不会重新记录：

1. `onboarding_first_open`：首次进入应用前台。
2. `onboarding_notification_granted`：首次观察到通知使用权已开启。
3. `onboarding_first_playback`：首次观察到播放器正在播放。
4. `onboarding_first_home_lyrics`：首次在主页提供非空歌词文档。
5. `onboarding_overlay_granted`：首次观察到悬浮窗权限已开启。
6. `onboarding_first_overlay`：首次成功创建悬浮窗。
7. `onboarding_first_overlay_lyrics`：首次向悬浮窗提供非空歌词文档。

步骤不是必须依次完成：主页显示歌词不需要悬浮窗权限。Umami 漏斗可选「first_open → notification_granted → first_playback → first_home_lyrics」，悬浮歌词另看「first_open → overlay_granted → first_overlay → first_overlay_lyrics」。用户可能先授权再开始播放或先开悬浮窗；严格顺序漏斗不能作为唯一转化指标。

属性 `cohort` 区分 `new_install`（安装此统计版本的新用户）、`existing_install`（升级用户）、`reenabled`（关闭后重新开启统计）；`elapsed_seconds` 是从本轮首次启用这套步骤统计到观察该步骤的时间。对升级用户的事件表示升级后首次观察，不能当作他们第一次使用应用。关闭后再开启会生成新标识和新一轮步骤。

## 崩溃诊断

只捕获 Java/Kotlin 未捕获异常。沿用 Android 原有崩溃处理，不在崩溃线程联网。私有存储最多保存最近一份 32 KB 报告，启动时删除超过七天的记录；有效记录在下次启动通过同一个 Umami 网站的 `app_crash` 事件补传；发送成功后删除，关闭统计立即删除。报告与随机安装标识绑定，不会在重置标识后补传旧报告。

报告包含 `kind=jvm_uncaught`、`report_id`、`crash_time`、`app_version`、Android 与机型、`exception_type`、`cause_type`、`main_thread`、`fingerprint`，以及最多十个 `frame_1` 至 `frame_10` 代码位置。不读取异常消息、变量、文件内容或完整日志。跨版本启动补传时 `app_version` 仍是崩溃时的版本。Umami 的事件时间是补传时间，实际发生时间看 `crash_time`。网络重试可能重复，用 `report_id` 辨认。

正式包经 R8 优化，代码位置可能混淆。对应版本的 Actions 构建会保存 `lyrics-android-mapping-<版本>`，保留 90 天，可下载 `mapping.txt` 归档并用 R8 retrace 还原。此实现不是 Umami 网页 JavaScript 错误采集；请在事件页查看 `app_crash`。不覆盖 ANR、原生信号崩溃、系统直接杀进程或没有再次启动的用户，不能据此声称全部崩溃率或实时监控。

## 统计口径与数据范围

`usage_time.seconds` 按数值求和，不能把事件条数当时长。前台与悬浮窗口可能同时运行，二者时长不能相加当作独立总时长。熄屏暂停计时，不使用唤醒锁；系统杀进程可能丢失最后不足一分钟的时长。

Umami 访客和会话数不是注册用户人数。本应用没有账号，以不参与备份的随机安装标识关联事件；清除数据、重装、关闭后重新开启统计都会生成新标识。访客数仍受服务端会话算法和网络变化影响，适合观察趋势，不能当作精确安装量。

使用 `/api/send`、Android User-Agent 和 Umami 返回的缓存标记。同一进程顺序发送，内存队列最多 100 条，超过 15 分钟丢弃；暂时性网络故障最多尝试三次。进程退出会丢弃未发送的普通事件。首次步骤和播放器的本机去重在排队时保存，网络失败可能造成漏计；上报是尽力而为，不用于计费或精确计数。

关闭统计取消计时、停止后续上报、断开连接、清空待发送记录（包括崩溃）、会话缓存、安装标识和步骤去重状态，已被服务器接收的数据不会自动删除。统计文件保存在 `noBackupFilesDir`。

不发送歌名、歌手、歌词正文、听歌历史、通知正文、已安装应用列表、API 地址/密钥、自定义模型名或歌词文件名，也不读取 Android ID、IMEI、序列号或广告 ID。Umami 和前置代理仍接收 IP 与请求时间，可能产生地区统计，应按隐私说明管理服务端数据。

基础统计从 1.2.5 提供；本页新增诊断、功能、步骤及设备维度从 1.2.7 提供，用户升级后才会产生这些数据。

参考：[HTTP 上报接口](https://umami.is/docs/api/sending-stats)、[Distinct IDs](https://umami.is/docs/distinct-ids)。
