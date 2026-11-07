# 使用情况统计（Umami）

应用直接使用 Umami 的 HTTP 上报接口，不加载网页统计脚本，也不嵌入管理员密码或 API 登录令牌。

- 后台：<https://umi.luoh.org>
- Website ID：`c8a3563b-bf82-40c0-8352-de5438f4c653`
- 网站名称：桌面歌词 Android
- 统计域名：`lyrics-android.local`，只用作事件的 hostname，不需要 DNS 解析。

接口配置在 `app/src/main/kotlin/com/luoh/music/lrc/UsageAnalytics.kt`。没有本机统计设置时默认开启，首次打开显示一次简短说明，不弹出授权对话框。设置入口在「设置 → 关于应用 → 使用情况统计」，隐私说明页也有开关；已有的关闭选择在正常升级和重启后继续保留。仅正式包 `com.luoh.music.lrc` 上报，`.dev` 测试包不会混入正式统计。

## 在后台看什么

打开这个网站项目，先选时间范围。概览里的 `/app` 和 `/overlay` 代表应用前台和悬浮歌词启动；事件及事件属性用于分析具体功能。

| 事件 | 含义 | 可查看的属性 |
| --- | --- | --- |
| `app_start` / `app_stop` | 应用进入、离开前台，设置页也计入；短暂活动切换不另开会话 | `app_version`、`android_api` |
| `overlay_start` / `overlay_stop` | 悬浮窗口成功创建、服务关闭 | `app_version`、`android_api` |
| `usage_time` | 每分钟或离开时累计亮屏使用时长 | `surface=app/overlay`、`seconds` |
| `player_used` | 当前正在播放的播放器类别，同一进程在每个 UTC 日期每类最多一条 | `player`，未知播放器统一为 `other` |
| `lyrics_lookup_result` | 一次联网查找歌词的最终结果，不包含本地缓存命中 | `source`、`result`、`duration_ms`、`failure` |
| `lyrics_provider_result` | 联网查找时对某个歌词源的尝试结果，一次查找可对应多条 | `source`、`result`、`duration_ms`、`failure` |
| `translation_result` | 一次补译任务的结果，包括缓存返回的译文 | `mode=offline/api`、`result`、`duration_ms`、`translated_lines`、`failure` |

所有事件附带应用版本与 Android API 级别。比较版本时按 `app_version` 筛选；比较离线与 API 翻译时按 `mode` 筛选。

`result` 为 `success`、`not_found`、`error`、`cancelled` 或 `skipped`。计算失败率时将取消任务、无需翻译的跳过单独列出。歌词源失败不一定代表最终查找失败，应通过 `lyrics_lookup_result` 观察整体结果。`failure` 只有固定分类，例如 `timeout`、`network`、`model_missing`、`configuration`、`authentication`、`rate_limit`；不上传异常原文。

`usage_time.seconds` 应按数值求和，不应把事件条数当时长。应用前台与悬浮窗口可以同时使用，两个 surface 的时长有重叠，不能相加当作独立使用总时长。熄屏暂停计时，不使用唤醒锁；系统杀死进程时，最后不足一个统计周期的时长可能丢失。

Umami 自带的访客和会话数并不等于注册用户人数。本应用没有账号，使用保存在本机且不参与备份的随机安装标识关联事件；清除应用数据、重装、关闭后重新开启统计都会产生新标识。Umami 的默认访客数仍受其会话算法与网络变化影响，应按趋势解读，不能当作精确安装量。

## 上传与数据范围

统计开启时生成随机安装标识并排队上传；没有已保存的设置时默认开启，已保存为关闭时不会重新启用。请求使用 `/api/send`、Android User-Agent 和 Umami 返回的缓存标记；同一进程顺序发送，避免同一会话并发创建。事件队列仅在内存中，最多 100 条，超过 15 分钟丢弃；暂时性网络故障最多尝试 3 次。进程退出会丢弃尚未发送的事件，网络重试可能产生重复，因此数据适合分析使用趋势，而非计费或精确计数。

关闭统计时取消计时、停止后续上报、断开正在使用的连接、清空队列与会话缓存，并清除安装标识。已经被服务器接收的数据不会自动删除。统计选择和标识保存在 `noBackupFilesDir`，不会通过 Android 备份复制到另一台设备。

客户端不发送歌名、歌手、歌词正文、听歌历史、通知正文、已安装应用列表、API 地址或密钥，也不读取 Android ID、IMEI 或广告 ID。Umami 和部署在前面的代理仍会接收请求 IP 与时间，并可能产生地区统计，应按应用隐私说明管理服务端数据。

从 1.2.5 起包含此功能，用户安装后默认开始统计。已有的 1.2.4 安装包不会自动增加上报能力。

参考：[HTTP 上报接口](https://umami.is/docs/api/sending-stats)、[Distinct IDs](https://umami.is/docs/distinct-ids)。
