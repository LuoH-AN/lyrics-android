/* This demo uses original sample text only. No music, microphone or network access. */
(() => {
  'use strict';
  const stage = document.querySelector('#demo-stage');
  const overlay = document.querySelector('#lyric-overlay');
  if (!stage || !overlay) return;

  const line = document.querySelector('#demo-line');
  const translation = document.querySelector('#demo-translation');
  const translationToggle = document.querySelector('#translation-toggle');
  const examples = [
    ['让风慢慢经过', 'Let the breeze take its time'],
    ['把今天调成喜欢的模样', 'Make today feel like you'],
    ['每个平凡瞬间，都值得听见', 'Every little moment deserves a song'],
    ['下一段旅程，带上这首歌', 'Take a song along for the journey'],
  ];
  let exampleIndex = 0;
  let position = { x: 0.5, y: 0.73 };
  let drag = null;
  const clamp = (value, min, max) => Math.min(max, Math.max(min, value));
  const bounds = () => ({
    x: Math.max(0, stage.clientWidth - overlay.offsetWidth - 16),
    y: Math.max(0, stage.clientHeight - overlay.offsetHeight - 16),
  });
  const renderPosition = () => {
    const available = bounds();
    overlay.style.left = `${8 + position.x * available.x}px`;
    overlay.style.top = `${8 + position.y * available.y}px`;
  };
  const moveTo = (left, top) => {
    const available = bounds();
    position = {
      x: available.x ? clamp((left - 8) / available.x, 0, 1) : 0,
      y: available.y ? clamp((top - 8) / available.y, 0, 1) : 0,
    };
    renderPosition();
  };

  document.querySelectorAll('.demo-wrap button').forEach(button => { button.disabled = false; });
  document.querySelectorAll('.swatches button').forEach(button => {
    button.addEventListener('click', () => {
      stage.dataset.theme = button.dataset.theme;
      document.querySelectorAll('.swatches button').forEach(item => {
        item.setAttribute('aria-pressed', String(item === button));
      });
    });
  });
  document.querySelectorAll('.context-controls button').forEach(button => {
    button.addEventListener('click', () => {
      stage.dataset.context = button.dataset.context;
      document.querySelectorAll('.context-controls button').forEach(item => {
        item.setAttribute('aria-pressed', String(item === button));
      });
    });
  });
  translationToggle.addEventListener('click', () => {
    translation.hidden = !translation.hidden;
    translationToggle.setAttribute('aria-pressed', String(!translation.hidden));
    renderPosition();
  });
  document.querySelector('#next-line').addEventListener('click', () => {
    exampleIndex = (exampleIndex + 1) % examples.length;
    [line.textContent, translation.textContent] = examples[exampleIndex];
    renderPosition();
  });
  document.querySelector('#reset-position').addEventListener('click', () => {
    position = { x: 0.5, y: 0.73 };
    renderPosition();
  });

  overlay.addEventListener('pointerdown', event => {
    if (!event.isPrimary || event.button !== 0) return;
    overlay.focus({ preventScroll: true });
    drag = { id: event.pointerId, x: event.clientX, y: event.clientY, left: overlay.offsetLeft, top: overlay.offsetTop };
    overlay.setPointerCapture(event.pointerId);
    overlay.classList.add('is-dragging');
  });
  overlay.addEventListener('pointermove', event => {
    if (!drag || drag.id !== event.pointerId) return;
    moveTo(drag.left + event.clientX - drag.x, drag.top + event.clientY - drag.y);
  });
  const finishDrag = event => {
    if (!drag || drag.id !== event.pointerId) return;
    drag = null;
    overlay.classList.remove('is-dragging');
    if (overlay.hasPointerCapture(event.pointerId)) overlay.releasePointerCapture(event.pointerId);
  };
  ['pointerup', 'pointercancel', 'lostpointercapture'].forEach(type => overlay.addEventListener(type, finishDrag));
  overlay.addEventListener('keydown', event => {
    const moves = { ArrowLeft: [-1, 0], ArrowRight: [1, 0], ArrowUp: [0, -1], ArrowDown: [0, 1] };
    const direction = moves[event.key];
    if (!direction) return;
    event.preventDefault();
    const step = event.shiftKey ? 24 : 8;
    moveTo(overlay.offsetLeft + direction[0] * step, overlay.offsetTop + direction[1] * step);
  });
  if ('ResizeObserver' in window) {
    const observer = new ResizeObserver(renderPosition);
    observer.observe(stage);
    observer.observe(overlay);
  } else {
    window.addEventListener('resize', renderPosition, { passive: true });
  }
  renderPosition();
})();
