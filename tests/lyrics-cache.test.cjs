const fs = require('node:fs');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const html = fs.readFileSync('app/src/main/assets/lyrics_overlay.html', 'utf8');
const values = new Map();
let epoch = 0;
const context = {
  LYRICS_CACHE_SCHEMA: 3, playback: { durationMs: 0 },
  window: { LobstaNativeLyrics: { lyricCacheEpoch: () => epoch } },
  isUsableLyricPayload: text => typeof text === 'string' && text.length > 0,
  localStorage: {
    get length() { return values.size; },
    key: index => [...values.keys()][index],
    getItem: key => values.get(key) || null,
    setItem: (key, value) => values.set(key, value),
    removeItem: key => values.delete(key),
  },
};
vm.createContext(context);
vm.runInContext(html.slice(html.indexOf('    function lyricCacheEpoch()'), html.indexOf('    function isUsableLyricPayload(')), context);
values.set('lyric-match-memory', 'manual selection');
values.set('lobsta-lyrics:expired', JSON.stringify({ schema: 3, savedAt: Date.now() - 15 * 86400000 }));
for (let index = 0; index < 65; index++) {
  values.set(`lobsta-lyrics:${index}`, JSON.stringify({ schema: 3, lyrics: 'line', savedAt: Date.now() - index * 1000 }));
}
context.pruneLyricsCache();
assert.equal(values.size, 61);
assert.ok(values.has('lobsta-lyrics:0'));
assert.ok(!values.has('lobsta-lyrics:64'));
assert.ok(!values.has('lobsta-lyrics:expired'));
context.saveLyrics('song', { lyrics: '[00:00.00]line', source: 'QQ音乐' });
assert.equal(context.cachedLyrics('song').lyrics, '[00:00.00]line');
epoch++;
assert.equal(context.cachedLyrics('song'), null, 'native clear invalidates the older browser cache');
context.pruneLyricsCache();
assert.equal(values.size, 1);
assert.equal(values.get('lyric-match-memory'), 'manual selection');
for (let index = 0; index < 3; index++) {
  values.set(`lobsta-lyrics:large${index}`, JSON.stringify({ schema: 3, epoch, lyrics: 'a'.repeat(1_000_000), savedAt: Date.now() - index * 1000 }));
}
context.pruneLyricsCache();
assert.ok([...values.entries()].filter(([key]) => key.startsWith('lobsta-lyrics:')).reduce((bytes, [, raw]) => bytes + raw.length * 2, 0) <= 4 * 1024 * 1024);
assert.ok(values.has('lobsta-lyrics:large0'));
assert.ok(!values.has('lobsta-lyrics:large2'));
console.log('PASS: bounded browser lyric cache, expiry, native cache clearing and preserved manual selections');
