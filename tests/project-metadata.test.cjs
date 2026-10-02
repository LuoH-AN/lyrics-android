const fs = require('node:fs');
const assert = require('node:assert/strict');
const read = name => fs.readFileSync(name, 'utf8');
const base = 'app/src/main/kotlin/com/luoh/music/lrc/';
const gradle = read('app/build.gradle.kts');
const version = /versionName = "([^"]+)"/.exec(gradle)[1];
const code = Number(/versionCode = (\d+)/.exec(gradle)[1]);
assert.match(version, /^\d+\.\d+\.\d+$/, 'stable version must be x.y.z');
assert.ok(code > 13, 'versionCode must upgrade the published 1.0.2 build');
assert.ok(gradle.includes('applicationId = "com.luoh.music.lrc"'));

const layout = read('app/src/main/res/layout/activity_settings.xml');
const activity = read(base + 'SettingsActivity.kt');
assert.ok(layout.includes('cell_maintainer') && layout.includes('维护者 · LuoH-AN'));
assert.ok(activity.includes('openUrl("https://github.com/LuoH-AN")'));
assert.ok(activity.includes('openUrl("https://github.com/LuoH-AN/lyrics-android")'));
assert.ok(activity.includes('.setMessage(R.string.privacy_summary)'), 'privacy information must be available offline');
assert.ok(read('app/src/main/res/values/strings.xml').includes('name="privacy_summary"'));
assert.doesNotMatch(activity, /PRIVACY\.md|LuoH-AN\/desktop-lyrics/);

const updater = read(base + 'UpdateChecker.kt');
assert.ok(updater.includes('https://github.com/LuoH-AN/lyrics-android/releases/latest'));
assert.ok(updater.includes('https://api.github.com/repos/LuoH-AN/lyrics-android/releases/latest'));
assert.ok(updater.includes('it.startsWith("https://github.com/LuoH-AN/lyrics-android/releases/")'));
assert.doesNotMatch(updater, /LuoH-AN\/desktop-lyrics/);
assert.ok(read(base + 'DirectLyricsRepository.kt').includes('https://github.com/LuoH-AN/lyrics-android'));

const workflow = read('.github/workflows/release.yml');
assert.ok(workflow.includes('assembleRelease') && workflow.includes('apksigner'));
assert.ok(workflow.includes('application-debuggable') && workflow.includes('RELEASE_CERT_SHA256'));
assert.ok(workflow.includes('482fae2e74bcf1cce5a4006dae7c86795b6eeb0112154cff462b20e4dd07300c'));
assert.ok(workflow.includes('--verify-tag --latest'));
assert.ok(workflow.includes('lyrics-android-${RELEASE_VERSION}-arm64-v8a.apk'));
for (const name of ['build', 'release']) {
  const text = read(`.github/workflows/${name}.yml`);
  assert.ok(text.includes('node tests/run.cjs'), 'both workflows must run the complete regression suite');
  assert.doesNotMatch(text, /desktop-lyrics/);
}
console.log(`PASS: ${version} version, repository links, offline privacy and signed-release checks`);
