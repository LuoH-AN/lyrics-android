const fs = require('node:fs');
const assert = require('node:assert/strict');
const path = require('node:path');

// Architecture boundaries complement the Compose semantics tests run by Android CI.
const base = 'app/src/main/kotlin/com/luoh/music/lrc';
const read = name => fs.readFileSync(path.join(base, name), 'utf8');
const gradle = fs.readFileSync('app/build.gradle.kts', 'utf8');
assert.match(gradle, /buildFeatures\s*\{\s*compose\s*=\s*true/);
assert.match(gradle, /org\.jetbrains\.kotlin\.plugin\.compose/);
assert.match(gradle, /androidx\.compose:compose-bom:2026\.05\.01/);
assert.match(gradle, /androidx\.compose\.ui:ui-test-junit4/);

const main = read('MainActivity.kt');
assert.match(main, /class MainActivity\s*:\s*ComponentActivity\(\)/);
assert.match(main, /setContent\s*\{/);
assert.match(main, /ComposeHomeScreen\(/);
assert.match(main, /SettingsOverlayHost\(/);
const navigation = read('ComposeNavigation.kt');
assert.match(navigation, /GuardedAnimatedContent\(/);
assert.match(navigation, /SettingsTwoPane\(/);
assert.match(navigation, /LocalSettingsScrollRetention provides/);
assert.match(navigation, /BackHandler\(/);

const pageSources = ['MainActivity.kt', 'ComposeNavigation.kt', 'ComposeHomeScreen.kt',
  'ComposeLibraryScreens.kt', 'ComposeSettingsScreens.kt', 'LyricsComposeTheme.kt'];
for (const name of pageSources) {
  const source = read(name);
  assert.match(source, /androidx\.compose\./, `${name} must render through Compose`);
  assert.doesNotMatch(source,
    /(?:import android\.widget\.(?:TextView|Button|EditText|LinearLayout|FrameLayout|ScrollView|SeekBar|Switch)\b|\b(?:AppCompatActivity|Fragment|LayoutInflater|AndroidView|ObjectAnimator|ValueAnimator|WebView)\b|\bsetContentView\s*\(|R\.layout\.)/,
    `${name} must not bridge back to the retired View page pipeline`);
}
assert.ok(!fs.readdirSync(base).some(name => name.endsWith('Page.kt')), 'retired page classes must be removed');
for (const name of ['HomeLyricsView.kt', 'HomeLyricMotion.kt', 'HomeMotionPreview.kt', 'AgoraPageHost.kt', 'AgoraSettingsScaffold.kt', 'NativeUi.kt']) {
  assert.ok(!fs.existsSync(path.join(base, name)), `${name} must be removed after migration`);
}
const layout = 'app/src/main/res/layout';
assert.ok(!fs.existsSync(layout) || !fs.readdirSync(layout).some(name => name.endsWith('.xml')),
  'application pages and dialogs must not retain XML layouts');

const originalComponents = ['SettingsOverlayHost.kt', 'SettingsAnimations.kt', 'SettingsScrollRetention.kt',
  'SettingsScaffold.kt', 'SettingsGroups.kt', 'CircularBackButton.kt', 'SmoothBottomSheet.kt',
  'AgoraMotionPolicy.kt', 'DialogWindowEdgeToEdge.kt', 'Theme.kt', 'Color.kt', 'Type.kt'];
for (const name of originalComponents) {
  const source = read('ui/agora/' + name);
  assert.match(source, /^package com\.luoh\.music\.lrc\.ui\.agora\b/m);
  assert.doesNotMatch(source, /\bAndroidView\b|\bFragment\b|R\.layout\./);
}
assert.ok(fs.existsSync(path.join(base, 'ui/agora/LICENSE')), 'upstream Compose components must retain their license');
const sheet = read('ui/agora/SmoothBottomSheet.kt');
assert.match(sheet, /Dialog\(/, 'the Agora sheet must use its original custom Compose dialog');
assert.match(sheet, /nestedScroll\(/, 'sheet and scrolling content must share gesture handoff');
assert.doesNotMatch(sheet, /\bModalBottomSheet\s*\(/, 'an unrelated Material sheet cannot replace the Agora shell');
assert.match(read('ComposeHomeScreen.kt'), /SmoothBottomSheet\(/);
assert.match(read('LyricsComposeTheme.kt'), /AgoraTheme\(/);
assert.match(read('LyricsComposeTheme.kt'), /ProvideAgoraMotionPolicy\(/);
for (const name of ['ComposeTransitionTest.kt', 'ComposeScrollRetentionTest.kt']) {
  assert.ok(fs.existsSync('app/src/test/kotlin/com/luoh/music/lrc/ui/agora/' + name));
}
console.log('PASS: Compose-only pages, original Agora components, custom sheet and semantics test boundaries');
