const fs = require('node:fs');
const assert = require('node:assert/strict');

// Copy checks protect user feedback; interaction is tested through Compose semantics in CI.
const base = 'app/src/main/kotlin/com/luoh/music/lrc/';
const readFeature = folder => fs.readdirSync(folder)
  .filter(name => name.endsWith('.kt'))
  .map(name => fs.readFileSync(folder + name, 'utf8'))
  .join('\n');
const library = readFeature(base + 'ui/library/');
const settings = readFeature(base + 'ui/settings/');
const english = fs.readFileSync('app/src/main/res/values/strings.xml', 'utf8');
const chinese = fs.readFileSync('app/src/main/res/values-zh/strings.xml', 'utf8');
const strings = xml => new Map(Array.from(xml.matchAll(/<string name="([^"]+)">([^<]*)<\/string>/g),
  ([, key, value]) => [key, value]));
const originalCopy = strings(chinese);
const translatedCopy = strings(english);
assert.deepEqual([...translatedCopy.keys()].sort(), [...originalCopy.keys()].sort());
for (const [key, original] of originalCopy) {
  const translated = translatedCopy.get(key);
  assert.ok(translated.length, `Missing English text for ${key}`);
  const placeholders = text => Array.from(text.matchAll(/%\d+\$[a-z]/g), match => match[0]).sort();
  assert.deepEqual(placeholders(translated), placeholders(original), `Different format placeholders for ${key}`);
}
const copyIsUsed = (source, text) => source.includes(text) || [...originalCopy].some(([key, value]) =>
  value.includes(text) && new RegExp(`R\\.string\\.${key}\\b`).test(source));
const picker = fs.readFileSync(base + 'ui/settings/SettingsColorDialog.kt', 'utf8');
for (const text of [
  '添加自定义歌词', '编辑自定义歌词', '歌名（必填）', '歌手（留空匹配所有同名歌曲）',
  'LRC 时间轴歌词', '[00:12.30]第一句歌词', '导入 .lrc / .yrc', '粘贴歌词',
  '已识别 %1$d 行歌词', '未识别到带时间轴的歌词行',
  '未保存的输入与导入内容将丢失。', '会恢复自动匹配歌词。', '新建自定义歌词',
  '暂无自定义歌词', '%1$d 行歌词', '删除后该歌曲将恢复自动匹配。',
  '搜索歌名或歌手', '暂无歌词匹配记录', '当前选择', '选用此版本',
  '此操作无法撤销。', '保留自定义歌词、翻译语言包与同步设置。',
  '同步记忆', '暂无同步记忆', '全部重置',
  '所有歌曲和歌词源都会恢复原始同步时间，此操作无法撤销。',
  '翻译方式', '离线语言包', 'HTTPS 服务地址', '模型名称', 'API Key',
  '保存并验证', '保留服务地址与模型。',
  '删除已缓存的补充译文，保留 API 配置与下载的语言包。',
]) assert.ok(copyIsUsed(library, text), `Compose library must preserve actionable feedback: ${text}`);
for (const text of [
  '歌词预览', '前文行数', '后文行数', '字重', '提前', '延后', '重置', '十六进制颜色', '输入 6 位颜色值',
]) assert.ok(copyIsUsed(settings, text), `Compose settings must preserve control copy: ${text}`);
for (const text of [
  '获取不到歌词时，手动指定一份标准 LRC 时间轴歌词。按歌名+歌手匹配，命中后主页和悬浮窗都直接使用这份。',
  '按歌曲整理尝试过的歌词版本，选择来源后可预览与切换。记录持续保留，可随时手动清理。',
  '示意背景 · 以下调整只影响桌面上的小歌词窗',
  '双语只控制显示；需要机翻时，请选择下面的补充方式。',
]) assert.ok(!copyIsUsed(library + settings, text), `retired helper copy must stay removed: ${text}`);
assert.match(settings, /OverlayAppearance\.percentToWeight\(/);
assert.match(settings, /OverlayAppearance\.weightToPercent\(/);
assert.match(picker, /testTag\("color_wheel"\)/, 'users must be able to pick arbitrary colors from a wheel');
for (const label of ['hue', 'saturation', 'brightness']) assert.ok(picker.includes(`ColorSlider(context.getString(R.string.${label})`), 'wheel must have accessible slider alternatives');
assert.doesNotMatch(picker, /val presets|presets\.forEach/, 'a few fixed swatches must not replace the color wheel');
assert.match(picker, /enabled = valid/, 'invalid colors must not be applied');
assert.doesNotMatch(settings, /lyric_offset_scope|拖动 RGB|seek_color_/);
console.log('PASS: Compose editor/import feedback, destructive-action explanations, settings copy and accessible palette controls');
