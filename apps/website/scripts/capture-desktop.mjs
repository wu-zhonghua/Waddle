// Capture the real local Electron renderer without cropping or modifying pixels.
import { writeFile } from 'node:fs/promises';
const [output] = process.argv.slice(2);
if (!output || !output.endsWith('.png')) throw new Error('Usage: node scripts/capture-desktop.mjs /tmp/screenshot.png');
const pages = await (await fetch('http://127.0.0.1:9225/json/list', { signal: AbortSignal.timeout(5000) })).json();
const candidates = pages.filter(page => page.type === 'page' && page.title.startsWith('Waddle -'));
if (candidates.length !== 1) throw new Error('Keep exactly one Waddle window open in the isolated screenshot instance.');
const endpoint = new URL(candidates[0].webSocketDebuggerUrl);
if (endpoint.hostname !== '127.0.0.1' || endpoint.port !== '9225') throw new Error('Only the local screenshot instance is allowed.');
const socket = new WebSocket(endpoint);
const timeout = setTimeout(() => { socket.close(); console.error('Screenshot timed out'); process.exitCode = 1; }, 10000);
try {
  await new Promise((resolve, reject) => {
    socket.addEventListener('open', resolve, { once: true });
    socket.addEventListener('error', reject, { once: true });
  });
  const result = await new Promise((resolve, reject) => {
    socket.addEventListener('message', event => {
      const message = JSON.parse(event.data);
      if (message.id === 1) message.error ? reject(new Error(message.error.message)) : resolve(message.result);
    });
    socket.addEventListener('close', () => reject(new Error('Screenshot connection closed')), { once: true });
    socket.send(JSON.stringify({ id: 1, method: 'Page.captureScreenshot', params: { format: 'png', captureBeyondViewport: false } }));
  });
  await writeFile(output, Buffer.from(result.data, 'base64'), { flag: 'wx' });
  console.log(`Saved original screenshot: ${output}`);
} finally {
  clearTimeout(timeout);
  socket.close();
}
