import { createScene, DURATION, clamp } from './scene.mjs';
import { makeSoundtrack } from './audio.mjs';

const canvas = document.querySelector('#film');
const stage = document.querySelector('#stage');
const loading = document.querySelector('#loading');
const playButton = document.querySelector('#play');
const soundButton = document.querySelector('#sound');
const timeline = document.querySelector('#timeline');
const clock = document.querySelector('#time');
const choices = [...document.querySelectorAll('[data-mode]')];
const reduced = window.matchMedia('(prefers-reduced-motion: reduce)');
const context = canvas.getContext('2d', { alpha: false });
const scene = createScene((width, height) => {
  const surface = document.createElement('canvas'); surface.width = width; surface.height = height; return surface;
});
let time = reduced.matches ? DURATION : 0;
let playing = false, ready = false, mode = 'auto', raf = 0, anchor = 0, lastClock = -1;
let sound = false, audioContext, audioBuffer, audioSource, resumeAfterScrub = false;

function prepareScore(sampleRate) {
  if (!window.Worker) return Promise.resolve(makeSoundtrack(sampleRate));
  return new Promise((resolve, reject) => {
    const worker = new Worker(new URL('./audio-worker.mjs', import.meta.url), { type: 'module' });
    worker.onmessage = ({ data }) => { worker.terminate(); resolve(data); };
    worker.onerror = (error) => { worker.terminate(); reject(error); };
    worker.postMessage(sampleRate);
  });
}

const format = (t) => `00:${Math.floor(t).toString().padStart(2, '0')}`;
function readTime() {
  if (playing) time = clamp((performance.now() - anchor) / 1000, 0, DURATION);
  return time;
}
function updateControls(force = false) {
  const tenth = Math.floor(time * 10);
  if (force || tenth !== lastClock) {
    timeline.value = String(time);
    timeline.setAttribute('aria-valuetext', `${time.toFixed(1)} 秒，共 ${DURATION} 秒`);
    clock.textContent = `${format(time)} / ${format(DURATION)}`;
    lastClock = tenth;
  }
}
function paint() {
  if (!ready) return;
  scene.draw(context, canvas.width, canvas.height, time, { mode, reducedMotion: reduced.matches });
  updateControls();
}
function stopAudio() {
  if (audioSource) { audioSource.stop(); audioSource.disconnect(); audioSource = null; }
}
function startAudio() {
  stopAudio();
  if (!sound || !playing || !audioBuffer || audioContext.state !== 'running' || time >= DURATION) return;
  audioSource = audioContext.createBufferSource(); audioSource.buffer = audioBuffer;
  audioSource.connect(audioContext.destination); audioSource.start(0, time);
}
function tick() {
  raf = 0; readTime(); paint();
  if (time >= DURATION) { pause(); return; }
  if (playing) raf = requestAnimationFrame(tick);
}
function play() {
  if (!ready || playing) return;
  if (time >= DURATION) time = 0;
  playing = true; anchor = performance.now() - time * 1000;
  playButton.textContent = '暂停'; canvas.setAttribute('aria-label', '介绍动画播放中，点击暂停。');
  startAudio(); raf = requestAnimationFrame(tick);
}
function pause() {
  readTime(); playing = false; cancelAnimationFrame(raf); raf = 0; stopAudio();
  playButton.textContent = time >= DURATION ? '重新播放' : '播放介绍';
  canvas.setAttribute('aria-label', '桌面歌词介绍已暂停，点击播放。'); updateControls(true);
}
function seek(value) {
  time = clamp(Number(value), 0, DURATION); anchor = performance.now() - time * 1000;
  paint(); updateControls(true); startAudio();
}
function toggle() { playing ? pause() : play(); }
playButton.addEventListener('click', toggle);
canvas.addEventListener('click', toggle);
canvas.addEventListener('keydown', (event) => {
  if (event.code === 'Space' || event.code === 'Enter') { event.preventDefault(); toggle(); }
  if (event.code === 'ArrowRight' || event.code === 'ArrowLeft') {
    event.preventDefault(); seek(readTime() + (event.code === 'ArrowRight' ? .5 : -.5));
  }
});
timeline.addEventListener('pointerdown', () => { resumeAfterScrub = playing; pause(); });
timeline.addEventListener('input', () => seek(timeline.value));
function endScrub() { if (resumeAfterScrub) { resumeAfterScrub = false; play(); } }
window.addEventListener('pointerup', endScrub);
window.addEventListener('pointercancel', endScrub);
timeline.addEventListener('keydown', (event) => {
  const delta = event.key === 'ArrowRight' ? .25 : event.key === 'ArrowLeft' ? -.25 : null;
  if (delta !== null) { event.preventDefault(); seek(readTime() + delta); }
});
for (const button of choices) button.addEventListener('click', () => {
  mode = button.dataset.mode;
  for (const choice of choices) choice.setAttribute('aria-pressed', String(choice === button));
  seek(mode === 'auto' ? 0 : 7.6); play();
});
soundButton.addEventListener('click', async () => {
  if (sound) {
    sound = false; stopAudio(); soundButton.textContent = '开启音乐'; soundButton.setAttribute('aria-pressed', 'false'); return;
  }
  soundButton.disabled = true;
  soundButton.textContent = '准备音乐…';
  try {
    audioContext ??= new AudioContext();
    await audioContext.resume();
    if (!audioBuffer) {
      const score = await prepareScore(audioContext.sampleRate);
      audioBuffer = audioContext.createBuffer(2, score.left.length, score.sampleRate);
      audioBuffer.copyToChannel(score.left, 0); audioBuffer.copyToChannel(score.right, 1);
    }
    sound = true; soundButton.textContent = '关闭音乐'; soundButton.setAttribute('aria-pressed', 'true');
    if (!document.hidden) { readTime(); if (!playing) play(); else startAudio(); }
  } catch {
    soundButton.textContent = '重试开启音乐';
  } finally { soundButton.disabled = false; }
});
document.addEventListener('visibilitychange', () => { if (document.hidden) { resumeAfterScrub = false; pause(); } });
reduced.addEventListener('change', () => { if (reduced.matches) pause(); paint(); });
const resize = new ResizeObserver(() => {
  const rect = stage.getBoundingClientRect();
  const ratio = Math.min(window.devicePixelRatio || 1, 2);
  canvas.width = Math.max(1, Math.round(rect.width * ratio)); canvas.height = Math.max(1, Math.round(rect.height * ratio)); paint();
});
resize.observe(stage);
try {
  await document.fonts.load('650 80px "Motion Sans"', '让每一句都有回响');
  scene.prepare(canvas.width, canvas.height);
  ready = true; stage.setAttribute('aria-busy', 'false'); loading.hidden = true;
  for (const button of [playButton, soundButton, ...choices]) button.disabled = false;
  timeline.disabled = false; paint();
  if (!reduced.matches && !document.hidden) play();
} catch {
  loading.textContent = '画面暂时未能加载，请刷新重试。'; stage.setAttribute('aria-busy', 'false');
}
