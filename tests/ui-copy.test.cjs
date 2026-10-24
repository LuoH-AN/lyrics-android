const fs = require('node:fs');
const assert = require('node:assert/strict');

// Copy checks protect user feedback; interaction is tested through Compose semantics in CI.
const base = 'app/src/main/kotlin/com/luoh/music/lrc/';
const library = fs.readFileSync(base + 'ComposeLibraryScreens.kt', 'utf8');
const settings = fs.readFileSync(base + 'ComposeSettingsScreens.kt', 'utf8');
for (const text of [
  '添加自定义歌词', '编辑自定义歌词', '歌名（必填）', '歌手（留空匹配所有同名歌曲）',
  'LRC 时间轴歌词', '[00:12.30]第一句歌词', '导入 .lrc 文件', '粘贴剪贴板',
  '识别到 ${parsed.lineCount} 行歌词', '没有识别到带时间轴的歌词行',
  '刚才输入或导入的歌词不会保存。', '会恢复自动匹配歌词。', '新建自定义歌词',
  '还没有自定义歌词', '${parsed.lineCount} 行歌词', '删除后这首歌会恢复自动匹配。',
  '搜索已记录的歌名或歌手', '暂无重匹配记录', '当前选择', '选用这个版本',
  '此操作无法撤销。', '自定义歌词、翻译语言包和同步设置都会保留。',
  '同步记忆', '还没有保存的同步调整', '重置全部同步记忆',
  '所有歌曲和歌词源都会恢复原始同步时间，此操作无法撤销。',
  '翻译方式', '离线语言包 · 需要代理', 'HTTPS 服务地址', '模型名称', 'API Key',
  '确认配置并验证连通性', '地址和模型仍会保留。',
  '删除已缓存的补充译文，保留 API 配置与下载的语言包。',
]) assert.ok(library.includes(text), `Compose library must preserve actionable feedback: ${text}`);
for (const text of [
  '歌词预览', '上文', '下文', '字重', '提前', '延后', '重置', '十六进制颜色', '输入 6 位颜色值',
]) assert.ok(settings.includes(text), `Compose settings must preserve control copy: ${text}`);
for (const text of [
  '获取不到歌词时，手动指定一份标准 LRC 时间轴歌词。按歌名+歌手匹配，命中后主页和悬浮窗都直接使用这份。',
  '按歌曲整理尝试过的歌词版本，选择来源后可预览与切换。记录持续保留，可随时手动清理。',
  '示意背景 · 以下调整只影响桌面上的小歌词窗',
  '双语只控制显示；需要机翻时，请选择下面的补充方式。',
]) assert.ok(!(library + settings).includes(text), `retired helper copy must stay removed: ${text}`);
assert.match(settings, /OverlayAppearance\.percentToWeight\(/);
assert.match(settings, /OverlayAppearance\.weightToPercent\(/);
assert.match(settings, /contentDescription = "颜色 \$hex"/, 'palette swatches must be accessible');
assert.match(settings, /enabled = valid/, 'invalid colors must not be applied');
assert.doesNotMatch(settings, /lyric_offset_scope|拖动 RGB|seek_color_/);
console.log('PASS: Compose editor/import feedback, destructive-action explanations, settings copy and accessible palette controls');
