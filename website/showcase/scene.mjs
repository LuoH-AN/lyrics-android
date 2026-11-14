// One deterministic visual timeline for the interactive page and offline MP4 renderer.
// All copy and sample lyrics are original. No network, wall clock, or random state here.
export const DURATION = 18;
export const MODES = ['staggered', 'centered', 'stairs'];
export const CHAPTERS = [0, 2.8, 7.5, 10.3, 13.5];
export const clamp = (v, lo = 0, hi = 1) => Math.min(hi, Math.max(lo, v));
const mix = (a, b, p) => a + (b - a) * p;
const smooth = (a, b, t) => { const p = clamp((t - a) / (b - a)); return p * p * (3 - 2 * p); };
const out = (p) => 1 - Math.pow(1 - clamp(p), 4);
const spring = (s) => s <= 0 ? 0 : 1 - Math.exp(-10 * s) * (Math.cos(13 * s) + 10 / 13 * Math.sin(13 * s));
const PAPER = '#f5f4f0';
const INK = '#23342b';
const GREEN = '#55765e';
const PALE = '#edf2e8';
const FONT = '"Motion Sans", sans-serif';

function roundRect(c, x, y, w, h, r) {
  c.beginPath(); c.moveTo(x + r, y); c.lineTo(x + w - r, y);
  c.quadraticCurveTo(x + w, y, x + w, y + r); c.lineTo(x + w, y + h - r);
  c.quadraticCurveTo(x + w, y + h, x + w - r, y + h); c.lineTo(x + r, y + h);
  c.quadraticCurveTo(x, y + h, x, y + h - r); c.lineTo(x, y + r);
  c.quadraticCurveTo(x, y, x + r, y); c.closePath();
}
function text(c, value, x, y, size, color = INK, weight = 600, align = 'left') {
  c.font = `${weight} ${size}px ${FONT}`; c.fillStyle = color;
  c.textAlign = align; c.textBaseline = 'alphabetic'; c.fillText(value, x, y);
}
function title(c, value, x, y, size, color, time, start, alpha = 1, align = 'left') {
  const p = out((time - start) / .8);
  if (p <= 0 || alpha <= 0) return;
  c.save(); c.globalAlpha *= p * alpha;
  text(c, value, x, y + (1 - p) * 65, size, color, 650, align); c.restore();
}
function mark(c, x, y, size, foreground = PALE, background = INK) {
  c.save(); c.translate(x, y); c.scale(size / 100, size / 100);
  c.fillStyle = background; roundRect(c, -50, -50, 100, 100, 28); c.fill();
  c.strokeStyle = foreground; c.lineWidth = 7; c.lineCap = 'round';
  for (const [yy, end] of [[-19, 22], [0, 11], [19, -2]]) {
    c.beginPath(); c.moveTo(-24, yy); c.lineTo(end, yy); c.stroke();
  }
  c.fillStyle = foreground; c.beginPath(); c.arc(24, 19, 6, 0, Math.PI * 2); c.fill(); c.restore();
}

const PHRASES = [
  { start: 3.7, end: 7.4, rows: ['让每一个字', '落在心跳上'], mode: 'staggered' },
  { start: 7.6, end: 10.3, rows: ['风穿过耳边', '音乐留在眼前'], mode: 'centered' },
  { start: 10.3, end: 13.5, rows: ['每一次跃动', '都刚刚好'], mode: 'stairs' },
];
const captions = { staggered: '错落，像一首诗。', centered: '居中，让每一拍落定。', stairs: '阶梯，让节奏向前。' };

function lyricLayout(phrase, width, height, mode) {
  let rows = phrase.rows;
  if (mode === 'staggered') {
    const first = Array.from(rows[0]);
    rows = [first.slice(0, 2).join(''), first.slice(2).join(''), rows[1]];
  }
  const characters = rows.map((row) => Array.from(row));
  const sizes = characters.map((row, r) => row.map((_, i) => 1 + [.04, .04, .18, .18, -.03, .08][(i + r * 2) % 6]));
  const units = sizes.map((row) => row.reduce((a, b) => a + b * 1.52, 0));
  const fontSize = Math.min(width / (Math.max(...units) + .6), height / (rows.length * 2.15));
  const total = characters.reduce((n, row) => n + row.length, 0);
  const interval = (phrase.end - phrase.start - .7) / total;
  let index = 0;
  return characters.flatMap((row, r) => {
    const rowWidth = units[r] * fontSize;
    const slack = (width - rowWidth) / 2;
    const offset = mode === 'staggered' ? (r % 2 ? 1 : -1) * Math.min(width * .10, slack)
      : mode === 'stairs' ? (r / Math.max(1, rows.length - 1) * 2 - 1) * Math.min(width * .10, slack) : 0;
    let x = -rowWidth / 2 + offset;
    return row.map((char, i) => {
      const size = fontSize * sizes[r][i];
      const token = { char, x: x + size * .76, y: (r - (rows.length - 1) / 2) * fontSize * 2.05,
        size, start: phrase.start + index * interval, duration: interval * 1.15, index, row: r };
      x += size * 1.52; index++; return token;
    });
  });
}

export function createScene(makeCanvas) {
  const surfaces = new Map();
  const layouts = new Map();
  function surface(key, w, h, paint) {
    if (!surfaces.has(key)) {
      const canvas = makeCanvas(w, h); paint(canvas.getContext('2d')); surfaces.set(key, canvas);
    }
    return surfaces.get(key);
  }
  function background(c, w, h, dark) {
    const image = surface(`background-${w}-${h}-${dark}`, w, h, (b) => {
      b.fillStyle = dark ? '#111c17' : PAPER; b.fillRect(0, 0, w, h);
      const glow = b.createRadialGradient(w * .7, h * .42, 0, w * .7, h * .42, w * .7);
      glow.addColorStop(0, dark ? '#2b4536' : '#e4e9d9');
      glow.addColorStop(1, dark ? '#111c17' : PAPER); b.fillStyle = glow; b.fillRect(0, 0, w, h);
    });
    c.drawImage(image, 0, 0);
  }
  function shell() {
    return surface('device', 960, 1480, (c) => {
      c.save(); c.shadowColor = '#17231c55'; c.shadowBlur = 65; c.shadowOffsetY = 45;
      c.fillStyle = '#7d877d'; roundRect(c, 180, 100, 600, 1200, 83); c.fill(); c.restore();
      const metal = c.createLinearGradient(180, 100, 780, 1200);
      metal.addColorStop(0, '#cbd3c9'); metal.addColorStop(.25, '#899387');
      metal.addColorStop(.48, '#e7eadf'); metal.addColorStop(.74, '#5b655c'); metal.addColorStop(1, '#c2cbbd');
      c.fillStyle = metal; roundRect(c, 180, 100, 600, 1200, 83); c.fill();
      c.fillStyle = '#101610'; roundRect(c, 188, 108, 584, 1184, 77); c.fill();
      const glass = c.createLinearGradient(200, 120, 740, 1280);
      glass.addColorStop(0, '#283d30'); glass.addColorStop(.55, '#18281e'); glass.addColorStop(1, '#101a15');
      c.fillStyle = glass; roundRect(c, 200, 120, 560, 1160, 65); c.fill();
      c.fillStyle = '#080d09'; c.beginPath(); c.arc(480, 145, 9, 0, Math.PI * 2); c.fill();
      c.fillStyle = '#899386'; roundRect(c, 780, 400, 5, 105, 2); c.fill();
      roundRect(c, 175, 320, 5, 135, 2); c.fill();
      text(c, '桌面歌词', 480, 240, 34, '#d8e4d4', 550, 'center');
      mark(c, 480, 935, 88, '#ecf0db', '#6b8464');
      text(c, '此刻，刚刚好', 480, 1035, 35, '#edf2e8', 550, 'center');
      c.fillStyle = '#ffffff30'; roundRect(c, 285, 1080, 390, 4, 2); c.fill();
      c.fillStyle = '#d4e4c5'; roundRect(c, 285, 1080, 140, 4, 2); c.fill();
      c.fillStyle = '#dbe8cd'; c.beginPath(); c.arc(480, 1160, 44, 0, Math.PI * 2); c.fill();
      c.fillStyle = '#263c2e'; roundRect(c, 467, 1144, 8, 32, 3); c.fill();
      roundRect(c, 485, 1144, 8, 32, 3); c.fill();
      c.strokeStyle = '#c8d6bd'; c.lineWidth = 5; c.lineCap = 'round';
      for (const [x, d] of [[360, -1], [600, 1]]) {
        c.beginPath(); c.moveTo(x - d * 10, 1148); c.lineTo(x + d * 10, 1160); c.lineTo(x - d * 10, 1172); c.stroke();
      }
      c.fillStyle = '#dde5d780'; roundRect(c, 405, 1255, 150, 5, 3); c.fill();
    });
  }
  function lyrics(c, time, width, height, mode, onlyFirst = false) {
    for (const phrase of PHRASES) {
      if (onlyFirst && phrase !== PHRASES[0]) continue;
      const opacity = smooth(phrase.start - .15, phrase.start + .08, time) * (1 - smooth(phrase.end - .18, phrase.end + .10, time));
      if (opacity <= 0) continue;
      const chosen = mode === 'auto' ? phrase.mode : mode;
      const key = `${phrase.start}-${width}-${height}-${chosen}`;
      if (!layouts.has(key)) layouts.set(key, lyricLayout(phrase, width, height, chosen));
      const tokens = layouts.get(key);
      c.save(); c.globalAlpha *= opacity;
      c.translate(Math.sin(time * .55) * width * .009, Math.sin(time * .9) * height * .012);
      for (const token of tokens) {
        const elapsed = time - token.start + .07;
        if (elapsed < 0) continue;
        const entered = spring(elapsed * 1.15);
        const passed = smooth(token.start + token.duration, token.start + token.duration + .3, time);
        const scale = mix(.48 + entered * .9, 1, passed);
        c.save(); c.globalAlpha *= clamp(elapsed / .06) * mix(1, .62, passed);
        c.translate(token.x + (1 - entered) * (token.index % 2 ? 12 : -12), token.y + (1 - entered) * 35);
        c.rotate((1 - entered) * .13 + Math.sin(token.index * 2.1) * .018 * (1 - passed));
        c.scale(scale, scale); c.font = `700 ${token.size}px ${FONT}`;
        c.textAlign = 'center'; c.textBaseline = 'middle'; c.fillStyle = PALE;
        if (passed < .2 && time >= token.start) { c.shadowColor = '#cfe8ad80'; c.shadowBlur = token.size * .15; }
        c.fillText(token.char, 0, 0); c.restore();
      }
      c.restore();
    }
  }
  return {
    prepare(pixelWidth, pixelHeight) {
      const portrait = pixelWidth < pixelHeight;
      const W = portrait ? 1080 : 1920, H = portrait ? 1440 : 1080;
      const scratch = makeCanvas(1, 1).getContext('2d');
      background(scratch, W, H, false); background(scratch, W, H, true); shell();
    },
    draw(c, pixelWidth, pixelHeight, time, { mode = 'auto', reducedMotion = false } = {}) {
      const t = clamp(time, 0, DURATION);
      const portrait = pixelWidth < pixelHeight;
      const W = portrait ? 1080 : 1920, H = portrait ? 1440 : 1080;
      c.save(); c.setTransform(pixelWidth / W, 0, 0, pixelHeight / H, 0, 0);
      c.globalAlpha = 1; background(c, W, H, false);
      const dark = smooth(6.85, 7.5, t) * (1 - smooth(13.05, 13.8, t));
      if (dark > 0) { c.save(); c.globalAlpha = dark; background(c, W, H, true); c.restore(); }

      const intro = 1 - smooth(2.25, 2.95, t);
      if (intro > 0) {
        c.save(); c.globalAlpha = intro;
        const s = portrait ? 110 : 142;
        mark(c, W / 2, H * .25, 64, PALE, INK);
        title(c, '让每一句', W / 2, H * .48, s, INK, t, .1, 1, 'center');
        title(c, '都有回响。', W / 2, H * .48 + s * 1.3, s, GREEN, t, .38, 1, 'center');
        title(c, '桌面歌词', W / 2, H * .78, portrait ? 56 : 36, GREEN, t, .85, 1, 'center'); c.restore();
      }

      const deviceAlpha = smooth(2.4, 3.0, t) * (1 - smooth(7.05, 7.75, t));
      if (deviceAlpha > 0) {
        const entering = out((t - 2.4) / 1.15);
        const zoom = smooth(6.8, 7.8, t);
        const x = mix(portrait ? W / 2 : W * .70, W / 2, zoom);
        const y = mix(portrait ? 870 : 530, H / 2, zoom) + (1 - entering) * 180;
        const scale = mix(portrait ? .77 : .67, portrait ? 1.7 : 1.85, zoom) * mix(.85, 1, entering);
        c.save(); c.globalAlpha = deviceAlpha; c.translate(x, y);
        c.rotate(reducedMotion ? 0 : mix(-.12, -.018 + Math.sin(t * .7) * .014, entering) * (1 - zoom));
        c.scale(scale, scale); c.drawImage(shell(), -480, -700);
        c.save(); roundRect(c, -280, -580, 560, 1160, 65); c.clip();
        c.translate(0, -75); lyrics(c, t, 460, 440, mode, true); c.restore(); c.restore();
      }

      const copy = smooth(2.7, 3.2, t) * (1 - smooth(6.5, 7.15, t));
      if (copy > 0) {
        const x = portrait ? W / 2 : 165;
        const align = portrait ? 'center' : 'left';
        const y = portrait ? 220 : 395;
        const s = portrait ? 80 : 112;
        title(c, '听见音乐。', x, y, s, INK, t, 2.75, copy, align);
        title(c, '看见节奏。', x, y + s * 1.24, s, GREEN, t, 2.95, copy, align);
        if (!portrait) title(c, '每个字，都在刚好的时刻跃起。', x + 4, y + 315, 32, GREEN, t, 3.25, copy);
      }

      const full = smooth(7.2, 7.75, t) * (1 - smooth(13.15, 13.65, t));
      if (full > 0) {
        c.save(); c.globalAlpha = full;
        const phrase = t < 10.3 ? PHRASES[1] : PHRASES[2];
        const chosen = mode === 'auto' ? phrase.mode : mode;
        const labelOpacity = smooth(phrase.start - .15, phrase.start + .25, t) * (1 - smooth(phrase.end - .15, phrase.end + .05, t));
        c.save(); c.globalAlpha *= labelOpacity;
        text(c, captions[chosen], W / 2, portrait ? 260 : 205, portrait ? 56 : 42, '#c6d8bd', 500, 'center'); c.restore();
        c.translate(W / 2, H * .55);
        lyrics(c, t, portrait ? 910 : 1410, portrait ? 740 : 610, mode); c.restore();
      }

      const ending = smooth(13.45, 14.2, t);
      if (ending > 0) {
        c.save(); c.globalAlpha = ending;
        const rise = reducedMotion ? 1 : out((t - 13.4) / 1.15);
        mark(c, W / 2, H * .29 + (1 - rise) * 75, portrait ? 138 : 114, PALE, INK);
        title(c, '桌面歌词', W / 2, H * .52, portrait ? 128 : 145, INK, t, 13.8, 1, 'center');
        title(c, '让音乐，留在眼前。', W / 2, H * .64, portrait ? 57 : 52, GREEN, t, 14.15, 1, 'center');
        title(c, '逐词跃动 · 悬浮歌词 · 双语显示', W / 2, H * .82, portrait ? 56 : 32, GREEN, t, 14.6, 1, 'center');
        c.restore();
      }
      c.restore();
    },
  };
}
