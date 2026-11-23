import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, readFile, readdir, rm, stat, writeFile } from 'node:fs/promises';
import { join } from 'node:path';
import { createHash } from 'node:crypto';
import { tmpdir } from 'node:os';
import { build, distributionFromArgs, root } from '../scripts/build.mjs';

const temp = await mkdtemp(join(tmpdir(), 'waddle-website-test-'));
const international = await build('international', temp);
const domestic = await build('domestic', temp);
test.after(() => rm(temp, { recursive: true, force: true }));

test('each language has real local navigation, complete images, and no download links', async () => {
  for (const dir of [international, domestic]) {
    for (const file of ['index.html', 'zh.html']) {
      const html = await readFile(join(dir, file), 'utf8');
      assert.match(html, file === 'index.html' ? /<html lang="en">/ : /<html lang="zh-CN">/);
      assert.equal([...html.matchAll(/<h1\b/g)].length, 1);
      assert.match(html, /href="\.\/index.html" lang="en"/);
      assert.match(html, /href="\.\/zh.html" lang="zh-CN"/);
      assert.equal([...html.matchAll(/aria-current="page"/g)].length, 1);
      for (const [, link] of html.matchAll(/(?:href|src)="([^"]+)"/g)) {
        if (link.startsWith('#')) {
          assert.ok(html.includes(`id="${link.slice(1)}"`), `Missing anchor ${link}`);
        } else if (link.startsWith('https:')) {
          assert.ok(link.startsWith('https://github.com/wu-zhonghua/Waddle'));
        } else {
          assert.ok((await stat(join(dir, link))).isFile(), `Missing resource ${link}`);
        }
      }
      for (const [, asset, width, height, alt] of html.matchAll(/<img src="\.\/assets\/([^"]+)" width="(\d+)" height="(\d+)" alt="([^"]+)"/g)) {
        const png = await readFile(join(dir, 'assets', asset));
        assert.equal(png.readUInt32BE(16), Number(width));
        assert.equal(png.readUInt32BE(20), Number(height));
        assert.ok(alt.length > 20);
      }
      const downloads = html.match(/<section id="downloads"[\s\S]*?<\/section>/)[0];
      assert.doesNotMatch(downloads, /<a\b|<button\b|download=/);
      assert.equal([...downloads.matchAll(/class="download-row"/g)].length, 4);
      assert.equal([...downloads.matchAll(file === 'index.html' ? /Coming soon/g : /即将推出/g)].length, 4);
      assert.doesNotMatch(html, /<script\b|<iframe\b/);
    }
  }
});

test('domestic artifacts exclude overseas services and international screenshots', async () => {
  for (const name of await readdir(domestic)) {
    if (name === 'assets') continue;
    const text = await readFile(join(domestic, name), 'utf8');
    assert.doesNotMatch(text, /codex|claude|antigravity|openai|chatgpt|anthropic|gemini/i, name);
  }
  // These exact screenshots were visually inspected. In particular, the
  // domestic Accounts screenshot itself contains an overseas protocol name,
  // so only its Settings entry is allowed here.
  assert.deepEqual((await readdir(join(domestic, 'assets'))).sort(), [
    'command-domestic.png', 'desktop-ai.png', 'desktop-workspace.png', 'settings-domestic.png', 'terminal-domestic.png', 'unfolded-terminal.png',
  ]);
  for (const name of ['index.html', 'zh.html']) {
    const html = await readFile(join(international, name), 'utf8');
    for (const service of ['Codex', 'Claude Code', 'Antigravity', 'OpenAI']) assert.ok(html.includes(service));
    assert.match(html, /not included yet|目前还没有包含/);
  }
});

test('screenshots are exact copies with accurate provenance and dimensions', async () => {
  const manifest = JSON.parse(await readFile(join(root, 'assets/screenshots.json'), 'utf8'));
  for (const [name, data] of Object.entries(manifest)) {
    const copy = await readFile(join(root, 'assets/screenshots', name));
    if (data.sha256) {
      assert.equal(createHash('sha256').update(copy).digest('hex'), data.sha256);
      assert.ok(data.capture && data.source && data.review);
    } else {
      assert.deepEqual(copy, await readFile(join(root, '../..', data.source)));
    }
    assert.equal(copy.readUInt32BE(16), data.width);
    assert.equal(copy.readUInt32BE(20), data.height);
  }
});

test('rebuilding one edition removes stale artifacts without deleting the other', async () => {
  await writeFile(join(domestic, 'assets', 'stale.png'), 'stale');
  const before = await readFile(join(international, 'index.html'));
  await build('domestic', temp);
  assert.ok(!(await readdir(join(domestic, 'assets'))).includes('stale.png'));
  assert.deepEqual(await readFile(join(international, 'index.html')), before);
});

test('invalid distribution flags fail instead of silently selecting international', () => {
  assert.equal(distributionFromArgs([]), 'international');
  assert.equal(distributionFromArgs(['--distribution', 'domestic']), 'domestic');
  assert.throws(() => distributionFromArgs(['--distribution', 'domestci']));
  assert.throws(() => distributionFromArgs(['--domestic']));
});


test('both editions introduce all four platforms and show both real clients in the hero', async () => {
  for (const dir of [international, domestic]) {
    for (const file of ['index.html', 'zh.html']) {
      const html = await readFile(join(dir, file), 'utf8');
      const hero = html.match(/<section class="hero"[\s\S]*?<\/section>/)[0];
      for (const platform of ['macOS', 'Windows', 'Linux', file === 'index.html' ? 'Android' : '安卓']) {
        assert.ok(hero.includes(platform), `Hero missing ${platform}`);
      }
      const images = [...hero.matchAll(/<img src="([^"]+)"/g)].map(match => match[1]);
      assert.equal(images.length, 2);
      assert.ok(images.some(src => src.endsWith('desktop-workspace.png')));
      assert.ok(images.some(src => /terminal(?:-domestic)?\.png$/.test(src)));
      assert.match(html, /aria-labelledby="desktop-features"/);
      assert.match(html, /aria-labelledby="phone-features"/);
      assert.doesNotMatch(html, /Free Beta AI credits|docs\.waveterm|docs\.waddle|iOS/i);
    }
  }
});
