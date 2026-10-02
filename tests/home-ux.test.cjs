const fs = require('node:fs');
const assert = require('node:assert/strict');

// Source-boundary checks only. Runtime behavior is covered by JVM/Robolectric tests in GitHub CI.
const base = 'app/src/main/kotlin/com/luoh/music/lrc/';
const main = fs.readFileSync(base + 'MainActivity.kt', 'utf8');
const home = fs.readFileSync(base + 'HomeLyricsView.kt', 'utf8');
const layout = fs.readFileSync('app/src/main/res/layout/activity_main.xml', 'utf8');
assert.doesNotMatch(main, /WebView|JavascriptInterface|evaluateJavascript|home_lyrics\.html/);
assert.doesNotMatch(layout, /<WebView\b|main_overlay_toggle|main_settings/);
assert.match(layout, /<com\.luoh\.music\.lrc\.HomeLyricsView/);
assert.match(layout, /com\.google\.android\.material\.button\.MaterialButton/);
assert.match(home, /BottomSheetDialog\(context\)/);
assert.match(home, /private val progress = Slider\(context\)/);
assert.match(home, /private val playerCard = MaterialCardView\(context\)/);
assert.doesNotMatch(home, /MaterialToolbar|home_toolbar|playbackStatus|home_playback_status/);
assert.doesNotMatch(home, /现在，专注听歌|等待播放|正在播放|已暂停/);
assert.match(home, /metadata\.addView\(utilities/);
assert.match(home, /trackHeight = dp\(6\)/);
assert.match(home, /thumbStrokeWidth = dp\(3\)/);
assert.match(home, /BottomSheetDragHandleView/);
assert.match(main, /LyricParser\.parse\(payload\.lyrics, payload\.translated, payload\.word\)/);
assert.match(main, /home\.setLyrics\(baseLyricDocument, currentLyricsCustom\)/);
assert.match(main, /HomeTranslationController\(/);
assert.match(home, /fun setTranslationStatus\(message: String\)/);
assert.match(main, /home\.setOverlayState\(LyricsOverlayService\.isRunning\)/);
assert.match(home, /utilities\.addView\(overlay, LayoutParams\(dp\(48\), dp\(48\)\)\)/);
assert.match(home, /utilities\.addView\(more, LayoutParams\(dp\(48\), dp\(48\)\)/);
assert.ok(home.indexOf('utilities.addView(overlay') < home.indexOf('utilities.addView(more'));
const overlaySetup = home.slice(home.indexOf('private val overlay'), home.indexOf('private val more'));
assert.match(overlaySetup, /actions\?\.toggleOverlay\(\)/);
assert.doesNotMatch(overlaySetup, /isChecked|isSelected|setOverlayState/, 'click must wait for the actual service state');
assert.match(home, /if \(running\) ColorUtils\.setAlphaComponent\(color\(R\.color\.text_primary\), 31\) else Color\.TRANSPARENT/);
assert.match(home, /iconSize = dp\(22\)/);
assert.match(home, /strokeWidth = 0/);
assert.ok(!fs.existsSync('app/src/main/assets/home_lyrics.html'), 'obsolete visible WebView page must be removed');
for (const test of ['LyricParserTest.kt', 'LyricClockTest.kt']) {
  assert.ok(fs.existsSync('app/src/test/kotlin/com/luoh/music/lrc/' + test));
}
console.log('PASS: native home source boundaries, Material controls, confirmed overlay state wiring, obsolete HTML removal');
