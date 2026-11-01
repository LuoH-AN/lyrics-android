import { mkdir, copyFile, writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const source = fileURLToPath(new URL('../', import.meta.url));
const root = fileURLToPath(new URL('../../', import.meta.url));
const output = path.join(source, 'dist');
await mkdir(path.join(output, 'assets'), { recursive: true });
for (const name of ['index.html', 'styles.css', 'app.js', 'sitemap.xml']) {
  await copyFile(path.join(source, name), path.join(output, name));
}
await copyFile(path.join(root, 'docs/assets/app-icon.svg'), path.join(output, 'assets/app-icon.svg'));
await writeFile(path.join(output, '.nojekyll'), '');
console.log(`Built static website: ${output}`);
