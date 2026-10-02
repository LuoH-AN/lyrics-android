const fs = require('node:fs');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const html = fs.readFileSync('app/src/main/assets/lyrics_overlay.html', 'utf8');

class Element {
  constructor() {
    this.className = '';
    this.children = [];
    this.dataset = {};
    this.textContent = '';
    this.style = { setProperty() {}, removeProperty() {} };
    this.classList = {
      contains: value => this.className.split(' ').includes(value),
      add: value => { if (!this.classList.contains(value)) this.className += ` ${value}`; },
      remove: value => { this.className = this.className.split(' ').filter(item => item !== value).join(' '); }
    };
  }
  appendChild(child) { this.children.push(child); }
  replaceChildren(...children) { this.children = children; }
  querySelector(selector) {
    const classes = [...selector.matchAll(/\.([\w-]+)/g)].map(match => match[1]);
    for (const child of this.children) {
      if (classes.every(value => child.classList.contains(value)) &&
          (!selector.includes('[data-translation="1"]') || child.dataset.translation === '1')) return child;
      const nested = child.querySelector(selector);
      if (nested) return nested;
    }
    return null;
  }
}
const states = [];
const context = {
  console,
  document: { createElement: () => new Element() },
  ui: { compactLyric: new Element() },
  playback: { active: 2, lyrics: Array.from({ length: 6 }, (_, i) => ({ t: i * 1000, text: `line ${i}` })) },
  translationMode: 'original',
  linePrimaryText: line => line.text,
  requestAnimationFrame: () => 1,
  cancelAnimationFrame() {},
  setTimeout: () => 1,
  clearTimeout() {},
  window: { LobstaNativeLyrics: {
    setCompactContentState: (lyrics, translated) => states.push([lyrics, translated])
  } }
};
vm.createContext(context);
vm.runInContext(html.slice(html.indexOf('    let contextBefore ='), html.indexOf('    function setLyricsTranslate(')), context);
vm.runInContext('contextBefore=2; contextAfter=2;', context);
context.setCompactLyric('line 2', false, '', 2, context.playback.lyrics[2], false);
let row = context.ui.compactLyric.children[0];
assert.deepEqual(row.children.filter(child => child.classList.contains('compact-context-line')).map(child => child.textContent), ['line 0', 'line 1', 'line 3', 'line 4']);
assert.deepEqual(states, [[true, false]]);

context.translationMode = 'bilingual';
context.setCompactLyric('line 2', false, 'translation', 2, context.playback.lyrics[2], true);
row = context.ui.compactLyric.children[0];
assert.equal(row.children.length, 6, 'translation is additional to the requested context');
assert.equal(row.querySelector('.compact-next-line').dataset.translation, '1');
assert.deepEqual(states.at(-1), [true, true]);

vm.runInContext('contextBefore=0; contextAfter=0;', context);
context.setCompactLyric('line 2', false, '', 2, context.playback.lyrics[2], false);
assert.equal(context.ui.compactLyric.children[0].children.length, 1, 'zero context means one current line');

vm.runInContext('contextBefore=2; contextAfter=2;', context);
context.setCompactLyric('line 0', false, '', 2, null, false, 0);
row = context.ui.compactLyric.children[0];
assert.deepEqual(row.children.filter(child => child.classList.contains('compact-context-line')).map(child => child.textContent), ['line 1', 'line 2'], 'plain lyrics use the displayed row index, without wrapping context at the start');
context.setCompactLyric('line 5', false, '', 2, null, false, 5);
row = context.ui.compactLyric.children[0];
assert.deepEqual(row.children.filter(child => child.classList.contains('compact-context-line')).map(child => child.textContent), ['line 3', 'line 4']);

context.playback.lyrics = [];
context.playback.active = -1;
context.setCompactLyric('找不到歌词', false);
assert.equal(context.ui.compactLyric.children[0].children.length, 1);
assert.deepEqual(states.at(-1), [false, false], 'empty lyrics must release context and translation height');
assert.ok(!html.includes('class="custom-lyrics-action"'), 'small overlay must not inject empty-state actions');

const service = fs.readFileSync('app/src/main/kotlin/com/luoh/music/lrc/LyricsOverlayService.kt', 'utf8');
assert.ok(!service.includes('FullscreenLyricsActivity'));
assert.ok(!service.includes('toggleCompact('));
assert.ok(!service.includes('revealCompactControls('));
assert.ok(service.includes('if (positionLocked) return true'));
assert.ok(service.includes('LinearLayout.LayoutParams(dp(48), dp(48))'));
assert.ok(service.includes('CompactLyricsView(uiContext)'), 'visible lyrics must use the native renderer');
assert.ok(service.includes('NativeUi.iconButton(uiContext'), 'overlay chrome must use Material buttons');
assert.ok(!/addView\((?:player|webView)\b/.test(service), 'matching WebView must never enter the visible overlay hierarchy');
assert.ok(!service.includes('webView?.post {'), 'unattached engines must use the main Handler rather than View.post');
console.log('PASS: compact context bounds, translation rows, plain-lyric context, empty sizing state, minimal native controls');
