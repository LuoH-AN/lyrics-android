import { makeSoundtrack } from './audio.mjs';

self.onmessage = ({ data: sampleRate }) => {
  const score = makeSoundtrack(sampleRate);
  self.postMessage(score, [score.left.buffer, score.right.buffer]);
};
