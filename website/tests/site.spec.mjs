import { test, expect } from '@playwright/test';
import { mkdir } from 'node:fs/promises';

const widths = [320, 390, 768, 1024, 1440];
for (const width of widths) {
  test(`responsive layout, assets and screenshots at ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: width < 800 ? 844 : 1000 });
    await page.emulateMedia({ reducedMotion: 'reduce' });
    const errors = [];
    const failed = [];
    const external = [];
    page.on('pageerror', error => errors.push(error.message));
    page.on('response', response => { if (response.status() >= 400) failed.push(response.url()); });
    page.on('request', request => { if (!request.url().startsWith('http://127.0.0.1:4173/')) external.push(request.url()); });
    await page.goto('/lyrics-android/');
    await expect(page.locator('h1')).toContainText('歌照常听');
    await expect(page.getByRole('button', { name: '下一句' })).toBeEnabled();
    // Inspect each section, not just the first screen.
    for (const section of await page.locator('main > section').all()) {
      await section.scrollIntoViewIfNeeded();
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1)).toBe(true);
    }
    await expect(page.locator('img, small, sup, sub, svg')).toHaveCount(0);
    expect(errors).toEqual([]);
    expect(failed).toEqual([]);
    expect(external).toEqual([]);
    await page.evaluate(() => scrollTo(0, 0));
    await mkdir('screenshots', { recursive: true });
    await page.screenshot({ path: `screenshots/home-${width}.png`, fullPage: true });
    if (width === 1440 || width === 390) {
      await page.screenshot({ path: `screenshots/hero-${width}.png` });
      await page.locator('.demo-wrap').screenshot({ path: `screenshots/demo-${width}.png` });
    }
  });

  test(`all visible text is at least 18px at ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 });
    await page.emulateMedia({ reducedMotion: 'reduce' });
    await page.goto('/');
    await page.locator('details').evaluateAll(items => items.forEach(item => { item.open = true; }));
    for (const name of ['阅读', '聊天']) {
      await page.getByRole('button', { name, exact: true }).click();
      for (let index = 0; index < 4; index++) {
        const undersized = await page.evaluate(() => {
          const walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);
          const failures = [];
          let node;
          while ((node = walker.nextNode())) {
            if (!node.textContent.trim()) continue;
            const element = node.parentElement;
            if (element.closest('script, style, noscript')) continue;
            const range = document.createRange();
            range.selectNodeContents(node);
            const style = getComputedStyle(element);
            if (!range.getClientRects().length || style.visibility === 'hidden') continue;
            const size = parseFloat(style.fontSize);
            if (size < 18) failures.push({ text: node.textContent.trim(), size });
          }
          return failures;
        });
        expect(undersized).toEqual([]);
        await expectInside(page);
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1)).toBe(true);
        await page.getByRole('button', { name: '下一句', exact: true }).click();
      }
    }
  });
}

async function expectInside(page) {
  const delta = await page.locator('#lyric-overlay').evaluate(element => {
    const overlay = element.getBoundingClientRect();
    const stage = element.parentElement.getBoundingClientRect();
    return { left: overlay.left - stage.left, top: overlay.top - stage.top, right: stage.right - overlay.right, bottom: stage.bottom - overlay.bottom };
  });
  Object.values(delta).forEach(value => expect(value).toBeGreaterThanOrEqual(6));
}

test('demo controls, mouse drag, keyboard, reset and resize bounds', async ({ page }) => {
  await page.goto('/');
  await page.getByRole('button', { name: '聊天', exact: true }).click();
  await expect(page.locator('#demo-stage')).toHaveAttribute('data-context', 'chat');
  await expect(page.getByRole('button', { name: '聊天', exact: true })).toHaveAttribute('aria-pressed', 'true');
  await page.getByRole('button', { name: '暖橙配色' }).click();
  await expect(page.locator('#demo-stage')).toHaveAttribute('data-theme', 'warm');
  await page.getByRole('button', { name: '双语', exact: true }).click();
  await expect(page.locator('#demo-translation')).toBeHidden();
  await expect(page.getByRole('button', { name: '双语', exact: true })).toHaveAttribute('aria-pressed', 'false');
  await page.getByRole('button', { name: '下一句' }).click();
  await expect(page.locator('#demo-line')).toHaveText('把今天调成喜欢的模样');
  await page.getByRole('button', { name: '双语', exact: true }).click();
  await expect(page.locator('#demo-translation')).toHaveText('Make today feel like you');
  const overlay = page.locator('#lyric-overlay');
  await overlay.scrollIntoViewIfNeeded();
  const initial = await overlay.boundingBox();
  await page.mouse.move(initial.x + initial.width / 2, initial.y + initial.height / 2);
  await page.mouse.down();
  await page.mouse.move(0, 0, { steps: 8 });
  await page.mouse.up();
  await expectInside(page);
  expect((await overlay.boundingBox()).x).toBeLessThan(initial.x);
  await overlay.focus();
  const beforeKey = await overlay.boundingBox();
  await page.keyboard.press('ArrowDown');
  expect((await overlay.boundingBox()).y).toBeGreaterThan(beforeKey.y);
  await page.getByRole('button', { name: '重置歌词位置' }).click();
  await expectInside(page);
  await page.setViewportSize({ width: 320, height: 844 });
  await page.getByRole('button', { name: '下一句' }).click();
  await expectInside(page);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1)).toBe(true);
});

test('touch drag stays in stage', async ({ browser }) => {
  const context = await browser.newContext({ viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true, reducedMotion: 'reduce' });
  const page = await context.newPage();
  await page.goto('http://127.0.0.1:4173/');
  await page.locator('#lyric-overlay').scrollIntoViewIfNeeded();
  const before = await page.locator('#lyric-overlay').boundingBox();
  const client = await context.newCDPSession(page);
  const point = { x: before.x + before.width / 2, y: before.y + before.height / 2 };
  await client.send('Input.dispatchTouchEvent', { type: 'touchStart', touchPoints: [point] });
  await client.send('Input.dispatchTouchEvent', { type: 'touchMove', touchPoints: [{ x: point.x - 35, y: point.y - 90 }] });
  await client.send('Input.dispatchTouchEvent', { type: 'touchEnd', touchPoints: [] });
  expect((await page.locator('#lyric-overlay').boundingBox()).y).toBeLessThan(before.y - 40);
  await expectInside(page);
  await context.close();
});

test('FAQ, downloads and reduced motion work without JavaScript', async ({ browser }) => {
  const context = await browser.newContext({ javaScriptEnabled: false, reducedMotion: 'reduce', viewport: { width: 390, height: 844 } });
  const page = await context.newPage();
  await page.goto('http://127.0.0.1:4173/');
  await expect(page.locator('h1')).toBeVisible();
  await expect(page.locator('#next-line')).toBeDisabled();
  const summary = page.locator('summary').first();
  await summary.click();
  await expect(page.locator('details').first()).toHaveAttribute('open', '');
  const downloads = page.locator('a[href="https://github.com/LuoH-AN/lyrics-android/releases/latest"]');
  await expect(downloads).toHaveCount(2);
  await page.locator('header a[href="#download"]').click();
  await expect(page.locator('#download-title')).toBeInViewport();
  expect(await page.locator('html').evaluate(element => getComputedStyle(element).scrollBehavior)).toBe('auto');
  await context.close();
});

test('keyboard skip link and FAQ interaction', async ({ page }) => {
  await page.goto('/');
  await page.keyboard.press('Tab');
  await expect(page.getByRole('link', { name: '跳转到内容' })).toBeFocused();
  await page.keyboard.press('Enter');
  await expect(page.locator('main')).toBeFocused();
  await page.locator('summary').first().focus();
  await page.keyboard.press('Enter');
  await expect(page.locator('details').first()).toHaveAttribute('open', '');
});
