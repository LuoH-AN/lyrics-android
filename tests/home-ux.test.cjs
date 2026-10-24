const fs = require('node:fs');
const assert = require('node:assert/strict');

const base = 'app/src/main/kotlin/com/luoh/music/lrc/';
const main = fs.readFileSync(base + 'MainActivity.kt', 'utf8');
const home = fs.readFileSync(base + 'ComposeHomeScreen.kt', 'utf8');
assert.doesNotMatch(main + home, /\bWebView\b|JavascriptInterface|evaluateJavascript|home_lyrics\.html/);
assert.match(home, /LazyColumn\(/, 'lyrics must be rendered in a real Compose scrolling list');
assert.match(home, /Slider\(/, 'playback progress must remain adjustable');
assert.match(home, /SmoothBottomSheet\(/, 'home actions must use the upstream Agora sheet');
for (const status of ['暂未播放', '正在获取歌词', '未找到歌词']) assert.ok(home.includes(status));
assert.doesNotMatch(home, /现在，专注听歌|先在音乐应用中播放一首歌|获取完成后会自动显示|home_empty_detail/);
assert.match(home, /state\.actions\?\.retryLyrics\(\)/);
assert.match(home, /state\.actions\?\.editCustomLyrics\(\)/);
assert.match(home, /state\.actions\?\.exportLrc\(\)/);
assert.ok(home.includes('下载当前歌词为 .lrc'));
assert.match(main, /LyricParser\.parse\(payload\.lyrics, payload\.translated, payload\.word\)/);
assert.match(main, /HomeTranslationController\(/);
assert.match(main, /exportLrcLauncher/);
assert.match(main, /LyricExporter\.buildLrc\(currentTrack, currentArtist, lrc, currentLrcTranslated\)/);
assert.match(main, /home\.setOverlayState\(LyricsOverlayService\.isRunning\)/,
  'overlay feedback must follow the actual service state');
const player = home.slice(home.indexOf('private fun HomePlayer('), home.indexOf('private fun HomeProgress('));
assert.match(player, /state\.actions\?\.toggleOverlay\(\)/);
assert.doesNotMatch(player, /overlayRunning\s*=|setOverlayState\(/, 'clicks must wait for service confirmation');
assert.match(home, /collectIsDraggedAsState\(/, 'manual scrolling must pause automatic lyric travel');
assert.match(home, /allowProgrammaticScrollMotion/, 'system and app motion policy must also apply to lyric travel');
assert.ok(!fs.existsSync('app/src/main/assets/home_lyrics.html'));
for (const test of ['LyricParserTest.kt', 'LyricClockTest.kt', 'LyricExporterTest.kt', 'HomeTranslationControllerTest.kt']) {
  assert.ok(fs.existsSync('app/src/test/kotlin/com/luoh/music/lrc/' + test));
}
console.log('PASS: Compose home, actionable lyric statuses, playback controls, preserved domain wiring and confirmed overlay state');
