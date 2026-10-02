const fs = require('node:fs');
const assert = require('node:assert/strict');
const path = require('node:path');

const source = 'app/src/main/kotlin/com/luoh/music/lrc';
const resources = 'app/src/main/res';
const theme = fs.readFileSync(path.join(resources, 'values/themes.xml'), 'utf8');
assert.ok(theme.includes('Theme.Material3.DayNight.NoActionBar'), 'the app must use a Material theme');
for (const name of fs.readdirSync(source).filter(name => name.endsWith('Activity.kt'))) {
  const text = fs.readFileSync(path.join(source, name), 'utf8');
  assert.ok(!/\b(?:Button|EditText|SwitchCompat|SeekBar|Dialog)\(this\b/.test(text), `${name} must use shared or explicit Material controls`);
  assert.ok(!/\bAlertDialog\.Builder\(/.test(text), `${name} must use Material dialogs`);
  assert.ok(!/\bWebView\b/.test(text), `${name} must not render a web page`);
}
for (const name of ['activity_main.xml', 'activity_settings.xml', 'dialog_color_picker.xml']) {
  const text = fs.readFileSync(path.join(resources, 'layout', name), 'utf8');
  assert.ok(!/<(?:Button|EditText|Switch|SeekBar|WebView)(?:\s|>)/.test(text), `${name} must declare native Material components`);
}
const native = fs.readFileSync(path.join(source, 'NativeUi.kt'), 'utf8');
assert.ok(native.includes('MaterialButton') && native.includes('TextInputLayout') && native.includes('MaterialAlertDialogBuilder'));
console.log('PASS: Material theme, native page boundaries, shared controls and dialog migration');
