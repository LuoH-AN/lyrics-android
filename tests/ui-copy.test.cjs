const fs = require('node:fs');
const assert = require('node:assert/strict');

// Source-boundary checks only; layout and runtime tests run in GitHub CI.
const base = 'app/src/main/kotlin/com/luoh/music/lrc/';
function checkCopy(file, removed, kept) {
  const source = fs.readFileSync(file, 'utf8');
  for (const text of removed) assert.ok(!source.includes(text), `${file} must remove helper: ${text}`);
  for (const text of kept) assert.ok(source.includes(text), `${file} must preserve UI feedback: ${text}`);
}

checkCopy(base + 'CustomLyricsEditActivity.kt', [
  '保存后，这首歌在主页和悬浮窗都直接使用这份歌词，不再联网搜索；删除即恢复自动匹配。',
  '格式：每行以 [分:秒.毫秒] 开头，例如 [01:23.45]歌词。同一时间戳写两行时，第二行作为译文显示；支持 [offset:毫秒] 整体偏移。',
], [
  '添加自定义歌词',
  '编辑自定义歌词',
  '歌名（必填）',
  '歌手（留空匹配所有同名歌曲）',
  'LRC 时间轴歌词',
  '[00:12.30]第一句歌词',
  '导入 .lrc 文件',
  '粘贴剪贴板',
  '识别到 ${parsed.lineCount} 行歌词',
  '没有识别到带时间轴的歌词行',
  '刚才输入或导入的歌词不会保存。',
  '会恢复自动匹配歌词。',
]);

checkCopy(base + 'CustomLyricsManagerActivity.kt', [
  '获取不到歌词时，手动指定一份标准 LRC 时间轴歌词。按歌名+歌手匹配，命中后主页和悬浮窗都直接使用这份。',
], [
  '手动填写歌名新建',
  '还没有自定义歌词',
  '已保存 ${entries.size} 份',
  '${parsed.lineCount} 行歌词',
  ' · 更新于 ${timeFormat.format(Date(entry.updatedAt))}',
  '删除后这首歌会恢复自动匹配。',
]);

checkCopy(base + 'LyricSourceManagerActivity.kt', [
  '按歌曲整理尝试过的歌词版本，选择来源后可预览与切换。记录持续保留，可随时手动清理。',
  '包括手动选定的版本和历史记录；自定义歌词、翻译语言包与同步设置不受影响。',
  '这是旧版保存的选择，暂不自动应用，请核对歌词后重新选用或删除。',
  '这是主页自动匹配写入的记录，还没有手动固定。选用某个版本后即固定，悬浮窗与主页都以它为准。',
], [
  '搜索已记录的歌名或歌手',
  '暂无重匹配记录',
  '✓ 当前选择 · ',
  '含逐字数据',
  '选用这个版本',
  '请求失败或来源暂时不可用，请稍后重试。',
  '此操作无法撤销。',
  '自定义歌词、翻译语言包和同步设置都会保留。',
]);

checkCopy(base + 'LyricOffsetMemoryActivity.kt', [
  '每首歌、每个歌词源独立保存。删除后恢复原始同步时间。',
], [
  '同步记忆',
  '还没有保存的同步调整',
  '重置全部同步记忆',
  'LyricSyncText.format(entry.offsetMs)',
  '所有歌曲和歌词源都会恢复原始同步时间，此操作无法撤销。',
]);

checkCopy(base + 'TranslationSettingsActivity.kt', [
  '平台译文优先，只在缺少译文时补充。译文会缓存到本机。',
  '点击对应方式立即应用；平台自带译文始终优先。',
  '兼容 Chat Completions 的服务',
  '可随时切换服务；每套地址、模型和密钥独立保存。缺少译文的歌词才会发送，密钥加密保存在本机。',
  '自动识别源语言。单个模型约 30 MB；翻译成中文还需要共用中文模型。系统不提供下载百分比和速度。',
], [
  '翻译方式',
  '离线语言包 · 需要代理',
  'HTTPS 服务地址',
  '模型名称',
  'API Key',
  '确认配置并验证连通性',
  '正在读取已下载语言包…',
  '只删除当前 API 配置的本机密钥，地址和模型仍会保留。',
  '删除已缓存的补充译文，保留 API 配置与下载的语言包。',
]);

checkCopy('app/src/main/res/layout/activity_settings.xml', [
  '示意背景 · 以下调整只影响桌面上的小歌词窗',
  '前后各可显示 0–2 句，窗口高度自动适应。前后都设为 0 时只保留当前句。',
  '歌词慢了就提前，歌词快了就延后。每首歌、每个歌词源分别记住。',
  '歌词来自 LRCLIB / QQ音乐 / 网易云，版权归各平台所有',
], [
  '歌词预览',
  '上下文展示',
  '前面几句',
  '后面几句',
  '播放一首歌后，可为这首歌单独校准',
  '@+id/lyric_offset_value',
  '提前 0.1 秒',
  '延后 0.1 秒',
  '恢复同步',
]);

console.log('PASS: confirmed helper copy removed; fields, actions, metadata, statuses and confirmation warnings preserved');
