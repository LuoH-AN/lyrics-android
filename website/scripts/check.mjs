import { readFile, stat } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';

const root = fileURLToPath(new URL('../dist/', import.meta.url));
const html = await readFile(path.join(root, 'index.html'), 'utf8');
const css = await readFile(path.join(root, 'styles.css'), 'utf8');
const js = await readFile(path.join(root, 'app.js'), 'utf8');
const ids = [...html.matchAll(/\bid="([^"]+)"/g)].map(match => match[1]);
assert.equal(ids.length, new Set(ids).size, 'HTML IDs must be unique');
assert.equal((html.match(/<h1\b/g) || []).length, 1);
assert.match(html, /<html lang="zh-CN">/);
assert.match(html, /name="viewport"/);
assert.match(html, /name="description"/);
const siteUrl = 'https://luoh-an.github.io/lyrics-android/';
assert.ok(html.includes(`<link rel="canonical" href="${siteUrl}">`), 'Canonical URL must match production');
assert.ok(html.includes(`<meta property="og:url" content="${siteUrl}">`), 'Open Graph URL must match production');
const sitemap = await readFile(path.join(root, 'sitemap.xml'), 'utf8');
assert.ok(sitemap.includes(`<loc>${siteUrl}</loc>`), 'Sitemap must match production');
assert.match(html, /<noscript>/);
assert.match(css, /prefers-reduced-motion/);
assert.doesNotMatch(html, /<(?:small|sup|sub|img|svg)\b/, 'No small print, decorative icons or tiny-text screenshot thumbnails');
assert.doesNotMatch(html + css, /eyebrow|fine-print|tiny-label|brand-en|orbit|grid-paper|floating-note|read-stamp/, 'Removed decoration must not return');
assert.doesNotMatch(css, /(?:linear|radial)-gradient|box-shadow|@keyframes/, 'No decorative textures, shadows or animations');
for (const [, value] of css.matchAll(/font-size:\s*(\d+(?:\.\d+)?)px/g)) {
  assert.ok(Number(value) >= 18, `Font size below 18px: ${value}px`);
}
assert.doesNotMatch(js, /\b(?:fetch|XMLHttpRequest|WebSocket|localStorage|setInterval)\b/, 'No network, storage or continuous timers');
let links = 0;
for (const [, target] of html.matchAll(/(?:href|src)="([^"]+)"/g)) {
  if (target.startsWith('https://')) continue;
  assert.ok(!target.startsWith('/'), `Asset must support project subpaths: ${target}`);
  if (target.startsWith('#')) {
    assert.ok(ids.includes(target.slice(1)), `Missing anchor ${target}`);
  } else {
    const local = path.resolve(root, target);
    assert.ok(local.startsWith(root), `Path outside site: ${target}`);
    assert.ok((await stat(local)).isFile(), `Missing asset ${target}`);
  }
  links++;
}
for (const [tag] of html.matchAll(/<img\b[^>]+>/g)) {
  assert.match(tag, /\balt="[^"]*"/);
  assert.match(tag, /\bwidth="\d+"/);
  assert.match(tag, /\bheight="\d+"/);
}
for (const [, names] of html.matchAll(/aria-(?:labelledby|describedby)="([^"]+)"/g)) {
  names.split(/\s+/).forEach(id => assert.ok(ids.includes(id), `Missing ARIA target ${id}`));
}
const download = 'https://github.com/LuoH-AN/lyrics-android/releases/latest';
assert.ok(html.split(download).length >= 3, 'Download links must point to real releases');
assert.match(html, /非真实歌曲歌词/);
assert.match(html, /不保证/);
assert.match(html, /tcrrry\/desktop-lyrics/);
execFileSync(process.execPath, ['--check', path.join(root, 'app.js')]);
console.log(`PASS: website metadata, ${links} local links/assets, ARIA references, download links and JS syntax`);
