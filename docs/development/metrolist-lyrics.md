# Metrolist 歌词获取参考

读取 [MetrolistGroup/Metrolist](https://github.com/MetrolistGroup/Metrolist)，版本为 `e6fea3b36c68841bad21daa6252e1d69ecd3afbe`。本项目复用系统 HTTP、JSON、Base64 和已有歌词解析器独立实现适合 MediaSession 元数据的查询流程。

## 上游流程

[`LyricsHelper.kt`](https://github.com/MetrolistGroup/Metrolist/blob/e6fea3b36c68841bad21daa6252e1d69ecd3afbe/app/src/main/kotlin/com/metrolist/music/lyrics/LyricsHelper.kt) 的普通获取路径按用户保存的来源顺序逐个查询，默认总预算 25 秒、单来源 8 秒，成功后返回；全部候选路径并行查询其他来源，在结果较少时再查询 LyricsPlus。内存候选缓存最多三项。

[`LyricsProviderRegistry.kt`](https://github.com/MetrolistGroup/Metrolist/blob/e6fea3b36c68841bad21daa6252e1d69ecd3afbe/app/src/main/kotlin/com/metrolist/music/lyrics/LyricsProviderRegistry.kt) 注册 BetterLyrics、LRCLIB、KuGou、Paxsenix、LyricsPlus、Zemer、YouTube Subtitle 和 YouTube。

| 来源 | 获取方式 | 本项目的适用情况 |
| --- | --- | --- |
| LRCLIB | 歌名、歌手、专辑和自由文本查询，按时长与元数据选候选，优先时间轴 | 补充专辑查询与自由文本回退，保留版本及歌手校验 |
| KuGou | 搜索歌曲、检查时长，再按歌曲 hash 查询歌词 ID 和 accesskey；缺失时关键词查询；下载 Base64 LRC | 新增直连来源，加入现有候选、缓存和版本重选流程 |
| BetterLyrics | 第三方 API 返回 TTML，由上游解析后转成 LRC | 可作为后续可选来源；接入逐词轨道需保留 TTML 的原始时间 |
| Paxsenix / LyricsPlus | 由第三方聚合 API 获取元数据匹配的歌词 | 可作为后续可选来源，需明确第三方数据传递与服务稳定性 |
| Zemer / YouTube / YouTube Subtitle | 根据 YouTube video ID 取得精确身份、歌词页或字幕 | 当前外部播放器通常只公开歌名、歌手和时长，不能将任意 MediaSession ID 当作 YouTube video ID |

## 已落实的改进

酷狗先按歌名、歌手和真实时长筛选歌曲，排除 Live 等版本冲突，再使用 hash 取得歌词。关键词回退仍校验身份；字段缺失时仅允许使用已验证的 hash 元数据。实查接口返回过对调的 `song/singer` 字段和混用秒、毫秒的记录时长，因此按已匹配歌曲选择字段及单位，并限制时长差异在 8 秒内。歌词中的时间戳保持原样。

每次酷狗查询最多实际下载四个候选，hash 和下载 ID 去重；排除用户已经试过的版本。损坏 Base64、非法 UTF-8、无时间轴、占位内容和只有歌名的歌词均跳过。线程取消后停止后续网络请求，响应仍有 2 MB 上限。使用现有并行预算与质量排序，QQ、网易的逐字和翻译覆盖率继续参与选择。

LRCLIB 首次查询可携带专辑；未命中后去掉专辑约束，最后补充歌手加歌名、仅歌名的 `q` 查询。扩大查询范围后仍检查歌手、版本与时长；未知时长不能成为仅歌名宽泛匹配的依据。

`DirectLyricsProvidersTest` 在 Android 8 和 Android 15 使用固定 HTTP 响应验证接口契约、版本与时长过滤、原始时间保留、元数据字段与单位差异、损坏响应、去重、取消，以及普通多源查询接入。它不依赖远端服务在测试期间可用；单次实查成功不代表所有歌曲或网络环境都可用。
