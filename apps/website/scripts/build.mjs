import { mkdir, readFile, writeFile, copyFile, rm } from 'node:fs/promises';
import { dirname, resolve, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { editions, copy } from '../src/content.mjs';
import { render } from '../src/render.mjs';

export const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
export function distributionFromArgs(args) {
  if (args.length === 0) return 'international';
  if (args.length === 2 && args[0] === '--distribution' && Object.hasOwn(editions, args[1])) return args[1];
  throw new Error('Usage: [--distribution international|domestic]');
}

export async function build(distribution = 'international', outputRoot = join(root, 'dist')) {
  if (!Object.hasOwn(editions, distribution)) throw new Error('Unknown distribution');
  for (const [key, translations] of Object.entries(copy)) {
    if (Object.keys(translations).sort().join(',') !== 'en,zh' || !translations.en || !translations.zh) throw new Error(`Incomplete translations: ${key}`);
  }
  const output = join(outputRoot, distribution);
  const screenshots = JSON.parse(await readFile(join(root, 'assets/screenshots.json'), 'utf8'));
  // Clean only this edition, so a previous build cannot leave unused assets behind.
  await rm(output, { recursive: true, force: true });
  await mkdir(join(output, 'assets'), { recursive: true });
  for (const file of new Set(Object.values(editions[distribution]))) {
    await copyFile(join(root, 'assets/screenshots', file), join(output, 'assets', file));
  }
  for (const file of ['styles.css', 'tokens.css', 'favicon.svg']) {
    await copyFile(join(root, 'src', file), join(output, file));
  }
  for (const language of ['en', 'zh']) {
    await writeFile(join(output, language === 'en' ? 'index.html' : 'zh.html'), render({ language, distribution, screenshots }));
  }
  return output;
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const output = await build(distributionFromArgs(process.argv.slice(2)));
  console.log(`Built ${output}`);
}
