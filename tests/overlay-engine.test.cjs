const fs = require('node:fs');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const html = fs.readFileSync('app/src/main/assets/lyrics_overlay.html', 'utf8');

class Element {
  constructor() {
    this.style = { setProperty() {}, removeProperty() {} };
    this.dataset = {};
    this.children = [];
    this.textContent = '';
    this.clientWidth = 0;
    this.classList = { add() {}, remove() {}, toggle() {}, contains: () => false };
  }
  addEventListener() {}
  setAttribute() {}
  removeAttribute() {}
  querySelectorAll() { return []; }
  querySelector() { return null; }
  appendChild(child) { this.children.push(child); }
  set innerHTML(value) { this.children = []; }
}
const elements = new Map();
const documents = [], requests = [], translations = [], translationStatuses = [], cache = new Map();
let custom = '', now = 0, animationFrames = 0, timers = 0, cancellations = 0;
const context = {
  console, AbortController, TextEncoder,
  performance: { now: () => now },
  document: {
    documentElement: new Element(), body: new Element(),
    getElementById(id) {
      if (!elements.has(id)) elements.set(id, new Element());
      return elements.get(id);
    },
    createElement: () => new Element(),
    querySelectorAll: () => [], addEventListener() {}
  },
  localStorage: { getItem: key => cache.get(key) || null, setItem: (key, value) => cache.set(key, value) },
  addEventListener() {},
  requestAnimationFrame() { animationFrames++; return 1; }, cancelAnimationFrame() {},
  setTimeout() { timers++; return timers; }, clearTimeout() {},
  LobstaNativeLyrics: {
    renderNativeLyrics: payload => documents.push(JSON.parse(payload)),
    customLyrics: () => custom,
    readMatchMemory: () => '[]', writeMatchMemory() {},
    lyricSourceOffset: () => 0,
    requestLyrics: (...args) => requests.push(args),
    cancelSupplement: () => cancellations++,
    supplementStrategy: () => JSON.stringify({ behind: 2, ahead: 12, prefetch: 4 }),
    reportSupplementStatus: payload => translationStatuses.push(JSON.parse(payload)),
    translateMissing: (epoch, payload) => translations.push({ epoch, rows: JSON.parse(payload) })
  }
};
context.window = context;
vm.createContext(context);
for (const match of html.matchAll(/<script(?:\s[^>]*)?>([\s\S]*?)<\/script>/g)) vm.runInContext(match[1], context);
const engine = context.LobstaOverlay;
const snapshot = { hasSession: true, track: 'Song', artist: 'Artist', durationMs: 10000, positionMs: 0, speed: 1, state: 'playing' };
engine.updatePlayback(snapshot);
assert.equal(requests.length, 1);
assert.equal(requests[0][5], false, 'hidden engine must not fetch unused artwork');
const firstRequest = requests[0][4];
engine.receiveLyrics(firstRequest, {
  source: 'QQ音乐', lyrics: '[00:00.00]first line\n[00:02.00]second line',
  translatedLyrics: '[00:00.00]第一句\n[00:02.00]第二句',
  wordLyrics: '[0,2000](0,700)first (700,1300)line\n[2000,2000](2000,700)second (2700,1300)line'
});
assert.equal(documents.at(-1).lines.length, 2);
assert.equal(documents.at(-1).lines[0].translation, '第一句');
assert.equal(documents.at(-1).lines[0].words[1].start, 700, 'native gets absolute word timing, not frame percentages');
assert.equal(documents.at(-1).track, 'Song');
assert.equal(animationFrames, 0, 'offscreen renderer must never wait for or schedule rAF');
assert.equal(timers, 0, 'native engine must not rely on throttled browser timers');

engine.updatePlayback({ ...snapshot, track: 'Next' });
const secondRequest = requests.at(-1)[4];
const countBeforeStale = documents.length;
engine.receiveLyrics(firstRequest, { source: 'QQ音乐', lyrics: '[00:00.00]stale song' });
assert.equal(documents.length, countBeforeStale, 'stale provider result cannot change current native document');
engine.receiveLyrics(secondRequest, { source: 'LRCLIB', lyrics: '[00:00.00]hello\n[00:02.00]world\n[00:04.00]again' });
now = 600;
engine.engineTick();
assert.equal(translations.length, 1, 'service tick starts translation without a browser timer');
engine.receiveSupplement(translations[0].epoch, { id: 0, text: '你好', source: '离线译' });
assert.equal(documents.at(-1).lines[0].translation, '你好', 'translation changes emit a native document directly');
assert.equal(animationFrames, 0);
assert.equal(timers, 0);

engine.updatePlayback({ hasSession: false });
assert.deepEqual(documents.at(-1).lines, []);
assert.equal(documents.at(-1).key, '');
assert.equal(documents.at(-1).message, '未在播放');
const clearedCount = documents.length;
engine.receiveLyrics(secondRequest, { source: 'LRCLIB', lyrics: '[00:00.00]stale after stop' });
engine.receiveSupplement(translations[0].epoch, { id: 0, text: 'stale translation', source: '离线译' });
assert.equal(documents.length, clearedCount, 'ended session invalidates both lyric and translation callbacks');
assert.ok(cancellations > 0);
engine.updatePlayback({ ...snapshot, track: 'Next' });
assert.ok(requests.at(-1)[4] > secondRequest, 'resuming the same song starts a fresh request generation');

custom = JSON.stringify({ source: '自定义歌词', custom: true, lyrics: '[00:01.00]my lyrics', duration: 9000 });
const networkBeforeCustom = requests.length;
engine.updatePlayback({ ...snapshot, track: 'Custom', durationMs: 0 });
assert.equal(requests.length, networkBeforeCustom, 'custom lyrics still bypass provider requests');
assert.equal(documents.at(-1).lines[0].text, 'my lyrics');
assert.equal(documents.at(-1).durationMs, 9000, 'fallback duration accompanies the emitted document');
assert.equal(animationFrames, 0);
assert.equal(timers, 0);
custom = '';
engine.updatePlayback({ ...snapshot, track: 'PartialOfficial' });
engine.receiveLyrics(requests.at(-1)[4], {
  source: 'QQ音乐', lyrics: '[00:00.00]hello\n[00:02.00]world\n[00:04.00]again',
  translatedLyrics: '[00:00.00]你好'
});
const beforePartial = translations.length;
now += 600;
engine.engineTick();
assert.equal(translations.length, beforePartial + 1, 'partial official translation must not disable missing-line supplementation');
assert.deepEqual(translations.at(-1).rows.map(row => row.id), [1, 2]);
const partialEpoch = translations.at(-1).epoch;
engine.receiveSupplement(partialEpoch, { id: 0, text: 'wrong overwrite', source: '机翻' });
assert.equal(documents.at(-1).lines[0].translation, '你好', 'official translation cannot be overwritten');
engine.receiveSupplement(partialEpoch, { id: 1, text: '世界', source: '机翻' });
assert.equal(documents.at(-1).lines[1].translation, '世界');
engine.receiveSupplement(partialEpoch, { status: 'error', message: '请先下载中文语言包' });
assert.equal(translationStatuses.at(-1).message, '请先下载中文语言包', 'native users must receive actionable translation failures');
assert.equal(translationStatuses.at(-1).track, 'PartialOfficial');
const beforeErrorTick = translations.length;
now += 20000;
engine.engineTick();
assert.equal(translations.length, beforeErrorTick, 'configuration errors must not retry on every service tick');
const beforeStaleStatus = translationStatuses.length;
engine.receiveSupplement(partialEpoch - 1, { status: 'error', message: 'old error' });
assert.equal(translationStatuses.length, beforeStaleStatus);
engine.setTranslationMode('original');
engine.updatePlayback({ ...snapshot, track: 'OriginalOnly' });
engine.receiveLyrics(requests.at(-1)[4], { source: 'LRCLIB', lyrics: '[00:00.00]hello' });
now += 600;
engine.engineTick();
assert.equal(translations.length, beforeErrorTick, 'original-only mode must never request machine translation');
console.log('PASS: native lyric engine, partial official translations, visible translation failures, stale callbacks, custom priority, and no hidden timers');
