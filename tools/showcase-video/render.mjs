import { createCanvas, GlobalFonts } from '@napi-rs/canvas';
import { spawn, execFile } from 'node:child_process';
import { Writable } from 'node:stream';
import { mkdir, writeFile, readFile, rm, stat } from 'node:fs/promises';
import { createHash } from 'node:crypto';
import { promisify } from 'node:util';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import { createScene, DURATION, MODES } from '../../website/showcase/scene.mjs';
import { makeSoundtrack } from '../../website/showcase/audio.mjs';

const root = fileURLToPath(new URL('../../', import.meta.url));
const fps = Number(process.env.VIDEO_FPS || 120);
const resolution = process.env.VIDEO_RESOLUTION || '1080p';
const mode = process.env.VIDEO_LAYOUT || 'auto';
if (![30, 60, 120].includes(fps) || !['1080p', '720p'].includes(resolution) || !['auto', ...MODES].includes(mode)) {
  throw new Error('Supported export options: 30/60/120 fps, 1080p/720p, auto/staggered/centered/stairs.');
}
const width = resolution === '1080p' ? 1920 : 1280, height = resolution === '1080p' ? 1080 : 720;
const directory = path.join(root, 'dist', 'showcase-video');
const basename = `lyrics-introduction-${resolution}-${fps}fps-${mode}`;
const target = path.join(directory, `${basename}.mp4`);
const audioPath = path.join(directory, 'soundtrack.wav');
await mkdir(directory, { recursive: true });
if (!GlobalFonts.registerFromPath(path.join(root, 'website/fonts/lyrics-motion.ttf'), 'Motion Sans')) {
  throw new Error('Could not load the bundled introduction font.');
}

const started = performance.now();
const { left, right, sampleRate } = makeSoundtrack();
const wav = Buffer.alloc(44 + left.length * 4);
wav.write('RIFF', 0); wav.writeUInt32LE(wav.length - 8, 4); wav.write('WAVEfmt ', 8);
wav.writeUInt32LE(16, 16); wav.writeUInt16LE(1, 20); wav.writeUInt16LE(2, 22);
wav.writeUInt32LE(sampleRate, 24); wav.writeUInt32LE(sampleRate * 4, 28);
wav.writeUInt16LE(4, 32); wav.writeUInt16LE(16, 34); wav.write('data', 36);
wav.writeUInt32LE(left.length * 4, 40);
for (let i = 0; i < left.length; i++) {
  wav.writeInt16LE(Math.round(Math.max(-1, Math.min(1, left[i])) * 32767), 44 + i * 4);
  wav.writeInt16LE(Math.round(Math.max(-1, Math.min(1, right[i])) * 32767), 46 + i * 4);
}
await writeFile(audioPath, wav);

const encoder = spawn('ffmpeg', ['-hide_banner', '-loglevel', 'warning', '-y',
  '-f', 'rawvideo', '-pixel_format', 'rgba', '-video_size', `${width}x${height}`, '-framerate', String(fps), '-i', 'pipe:0',
  '-i', audioPath, '-map', '0:v:0', '-map', '1:a:0',
  '-vf', 'scale=in_range=full:out_range=tv:out_color_matrix=bt709,format=yuv420p',
  '-c:v', 'libx264', '-preset', 'veryfast', '-crf', '20', '-threads', '2',
  '-color_primaries', 'bt709', '-color_trc', 'bt709', '-colorspace', 'bt709',
  '-c:a', 'aac', '-b:a', '192k', '-movflags', '+faststart', '-shortest', target],
  { stdio: ['pipe', 'ignore', 'pipe'] });
let encoderLog = '';
encoder.stderr.on('data', (chunk) => { encoderLog = (encoderLog + chunk).slice(-12000); });
const finished = new Promise((resolve, reject) => {
  encoder.once('error', reject);
  encoder.once('close', (code) => code === 0 ? resolve() : reject(new Error(`FFmpeg exited ${code}: ${encoderLog}`)));
});
// Observe an early encoder failure while the producer is awaiting stream backpressure.
finished.catch(() => {});
const writer = Writable.toWeb(encoder.stdin).getWriter();
const canvas = createCanvas(width, height), context = canvas.getContext('2d');
const scene = createScene(createCanvas);
scene.prepare(width, height);
const frames = Math.round(DURATION * fps);
console.log(`Rendering ${frames} frames, ${width}×${height}, ${fps} fps. No browser or test tasks.`);
try {
  for (let frame = 0; frame < frames; frame++) {
    scene.draw(context, width, height, frame / fps, { mode });
    const pixels = context.getImageData(0, 0, width, height).data;
    await writer.write(Buffer.from(pixels.buffer, pixels.byteOffset, pixels.byteLength));
    if (frame % (fps * 3) === 0) console.log(`Rendered ${(frame / fps).toFixed(0)} / ${DURATION} seconds`);
  }
  await writer.close(); await finished;
} catch (error) {
  encoder.kill(); await rm(target, { force: true }); throw error;
} finally { await rm(audioPath, { force: true }); }

const { stdout } = await promisify(execFile)('ffprobe', ['-v', 'error', '-show_entries',
  'stream=codec_type,codec_name,width,height,avg_frame_rate,nb_frames:format=duration,size', '-of', 'json', target]);
const metadata = { ...JSON.parse(stdout), layout: mode, sourceCommit: process.env.GITHUB_SHA || 'local',
  renderSeconds: Number(((performance.now() - started) / 1000).toFixed(1)) };
await writeFile(path.join(directory, 'render.json'), `${JSON.stringify(metadata, null, 2)}\n`);
const digest = createHash('sha256').update(await readFile(target)).digest('hex');
await writeFile(path.join(directory, 'SHA256SUMS'), `${digest}  ${path.basename(target)}\n`);
const bytes = (await stat(target)).size;
console.log(`Ready: ${target} (${(bytes / 1024 / 1024).toFixed(1)} MiB), rendered in ${metadata.renderSeconds}s`);
if (process.env.GITHUB_STEP_SUMMARY) {
  await writeFile(process.env.GITHUB_STEP_SUMMARY,
    `## 桌面歌词介绍视频\n\n${DURATION} 秒 · ${width} × ${height} · ${fps} fps · H.264 / AAC\n\n` +
    `文件：\`${path.basename(target)}\`，${(bytes / 1024 / 1024).toFixed(1)} MiB。渲染及编码用时 ${metadata.renderSeconds} 秒。\n\n` +
    `布局：${mode}。源提交：\`${metadata.sourceCommit}\`。未运行测试或浏览器截图任务。\n`);
}
