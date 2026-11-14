import { DURATION, clamp } from './scene.mjs';

// Original, deterministic stereo score. The browser and the video use these exact samples.
export function makeSoundtrack(sampleRate = 48000) {
  const length = Math.round(DURATION * sampleRate);
  const left = new Float32Array(length), right = new Float32Array(length);
  const frequency = (midi) => 440 * 2 ** ((midi - 69) / 12);
  function note(midi, start, duration, volume, pan = 0, pluck = false) {
    const from = Math.round(start * sampleRate), count = Math.round(duration * sampleRate);
    const f = frequency(midi);
    const lg = Math.sqrt((1 - pan) / 2), rg = Math.sqrt((1 + pan) / 2);
    for (let i = 0; i < count && from + i < length; i++) {
      const t = i / sampleRate;
      const attack = clamp(t / (pluck ? .008 : .25));
      const release = clamp((duration - t) / (pluck ? .12 : .45));
      const envelope = attack * release * (pluck ? Math.exp(-t * 5) : .72 + .28 * Math.sin(Math.PI * t / duration));
      const phase = 2 * Math.PI * f * t;
      const value = (Math.sin(phase) + .22 * Math.sin(phase * 2) + .05 * Math.sin(phase * 3)) * envelope * volume;
      left[from + i] += value * lg; right[from + i] += value * rg;
    }
  }
  const roots = [50, 57, 59, 55];
  for (let bar = 0; bar < 9; bar++) {
    const root = roots[bar % roots.length];
    const third = bar % 4 === 2 ? 3 : 4;
    for (const [offset, pan] of [[0, -.6], [third, .5], [7, -.25], [12, .65]]) {
      note(root + offset, bar * 2, 2.6, .039, pan);
    }
    for (let beat = 0; beat < 4; beat++) {
      note(root + [24, 19, 24 + third, 19][beat], bar * 2 + beat * .5, 1.0, .14, Math.sin(beat * 2 + bar) * .55, true);
      // A soft rounded pulse carries the cut without aggressive percussion.
      note(root - 12, bar * 2 + beat * .5, .28, .12, 0, true);
    }
  }
  let noise = 417;
  for (let beat = 0; beat < 35; beat++) {
    const from = Math.round((beat * .5 + .25) * sampleRate);
    for (let i = 0; i < sampleRate * .05 && from + i < length; i++) {
      noise = (Math.imul(noise, 1664525) + 1013904223) >>> 0;
      const value = (noise / 4294967296 * 2 - 1) * Math.exp(-i / sampleRate * 95) * .022;
      left[from + i] += value * .8; right[from + i] += value;
    }
  }
  for (let i = 0; i < length; i++) {
    const t = i / sampleRate;
    const envelope = clamp(t / .22) * clamp((DURATION - t) / 1.0);
    left[i] = Math.tanh(left[i] * 1.5) * envelope * .8;
    right[i] = Math.tanh(right[i] * 1.5) * envelope * .8;
  }
  return { left, right, sampleRate };
}
