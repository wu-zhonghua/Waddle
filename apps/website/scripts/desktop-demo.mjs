// Local-only screenshot fixtures. Never included in website build output.
import { createServer } from 'node:http';
import { mkdtemp, mkdir, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

if (process.argv[2] === 'prepare') {
  const dir = await mkdtemp(join(tmpdir(), 'waddle-website-demo-'));
  await mkdir(join(dir, 'config'));
  await mkdir(join(dir, 'demo'));
  await writeFile(join(dir, 'demo', 'README.md'), '# Waddle workspace\n\nA small project, ready to explore.\n\n- Terminal and files, side by side\n- Review changes before running commands\n- Keep the workspace yours\n');
  await writeFile(join(dir, 'demo', 'notes.txt'), 'Plan\nBuild a small tool.\nReview the output.\nKeep useful notes.\n');
  await writeFile(join(dir, 'config', 'settings.json'), JSON.stringify({
    'telemetry:enabled': false,
    'autoupdate:enabled': false,
    'autoupdate:installonquit': false,
    'waveai:showcloudmodes': false,
    'waveai:defaultmode': 'local-demo',
    'conn:localhostdisplayname': 'Local',
    'term:localshellpath': '/bin/bash',
    'term:localshellopts': ['--noprofile', '--norc'],
    'app:confirmquit': false,
  }, null, 2));
  await writeFile(join(dir, 'config', 'waveai.json'), JSON.stringify({
    'local-demo': {
      'display:name': 'Local model',
      'display:icon': 'microchip',
      'ai:provider': 'custom',
      'ai:apitype': 'openai-chat',
      'ai:model': 'local-demo',
      'ai:endpoint': 'http://127.0.0.1:9189/v1/chat/completions',
      'ai:capabilities': ['tools'],
    },
  }, null, 2));
  console.log(dir);
} else if (process.argv[2] === 'serve') {
  const server = createServer(async (req, res) => {
    if (req.method !== 'POST' || req.url !== '/v1/chat/completions') {
      res.writeHead(404).end();
      return;
    }
    // Drain the request without saving prompts, environment context or credentials.
    for await (const _ of req) { /* No request logging. */ }
    res.writeHead(200, { 'Content-Type': 'text/event-stream' });
    const chunk = (delta, finish_reason) => `data: ${JSON.stringify({
      id: 'demo-response', object: 'chat.completion.chunk',
      choices: [{ index: 0, delta, finish_reason }],
    })}\n\n`;
    res.write(chunk({ role: 'assistant', content: 'Local connection test complete. This is a deterministic demo response, not model inference. Your workspace stays under your control.' }, null));
    res.write(chunk({}, 'stop'));
    res.end('data: [DONE]\n\n');
  });
  server.listen(9189, '127.0.0.1', () => console.log('Local screenshot fixture: http://127.0.0.1:9189'));
  server.on('error', error => { console.error(error.message); process.exitCode = 1; });
  for (const signal of ['SIGINT', 'SIGTERM']) process.on(signal, () => server.close());
} else {
  console.error('Usage: node scripts/desktop-demo.mjs prepare|serve');
  process.exitCode = 1;
}
