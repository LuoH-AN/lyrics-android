const fs = require('node:fs');
const path = require('node:path');
const { spawnSync } = require('node:child_process');

const root = path.resolve(__dirname, '..');
const tests = fs.readdirSync(__dirname).filter(name => name.endsWith('.test.cjs')).sort();
if (tests.length === 0) throw new Error('No regression tests found');

for (const test of tests) {
  console.log(`\nRunning ${test}`);
  const result = spawnSync(process.execPath, [path.join(__dirname, test)], {
    cwd: root,
    stdio: 'inherit',
    timeout: 60_000,
  });
  if (result.error) throw result.error;
  if (result.status !== 0) process.exit(result.status || 1);
}
console.log(`\nPASS: all ${tests.length} regression test files`);
