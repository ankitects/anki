// Run only against a fresh Chrome profile and a dedicated test origin.
// Usage: npm run test:shared-browser -- <CDP port> <PWA URL>
import assert from "node:assert/strict";

const [port = "9232", origin = "http://127.0.0.1:3012/"] = process.argv.slice(2);
const targets = await (await fetch(`http://127.0.0.1:${port}/json`)).json();
const target = targets.find((candidate) => candidate.type === "page" && candidate.url === origin);
assert.ok(target, `Open ${origin} in a fresh Chrome profile with --remote-debugging-port=${port}`);
const socket = new WebSocket(target.webSocketDebuggerUrl);
await new Promise((resolve, reject) => { socket.onopen = resolve; socket.onerror = reject; });
let id = 0;
const pending = new Map();

function send(method, params = {}) {
  const requestId = ++id;
  return new Promise((resolve, reject) => {
    pending.set(requestId, (reply) => reply.error ? reject(new Error(JSON.stringify(reply.error))) : resolve(reply.result));
    socket.send(JSON.stringify({ id: requestId, method, params }));
  });
}

socket.onmessage = (event) => {
  const response = JSON.parse(event.data);
  const handler = pending.get(response.id);
  if (handler) { pending.delete(response.id); handler(response); }
};

async function evaluate(expression) {
  const reply = await send("Runtime.evaluate", { expression, awaitPromise: true, returnByValue: true });
  if (reply.exceptionDetails) throw new Error(JSON.stringify(reply.exceptionDetails));
  return reply.result?.value;
}

async function until(expression) {
  const deadline = Date.now() + 20_000;
  while (Date.now() < deadline) {
    if (await evaluate(`Boolean(${expression})`)) return;
    await new Promise((resolve) => setTimeout(resolve, 100));
  }
  throw new Error(`Timed out: ${expression}\n${await evaluate("document.body.innerText")}`);
}

try {
  await until('document.querySelector(".storage-banner")');
  await evaluate('[...document.querySelectorAll("button")].find((button) => button.textContent.trim() === "Get shared").click()');
  await until('document.querySelector("#shared-query")');
  assert.equal(await evaluate('document.querySelector(".shared-search").action'), "https://ankiweb.net/shared/decks");
  assert.equal(await evaluate('document.querySelector("#shared-query").name'), "search");
  assert.equal(await evaluate('document.querySelector("#shared-sort").name'), "sort");
  assert.match(await evaluate("document.body.innerText"), /Search AnkiWeb/);
  assert.match(await evaluate("document.body.innerText"), /Download the `.apkg`/);
  console.log(JSON.stringify({ sharedScreen: true, officialSearch: true, importWorkflow: true }));
} finally {
  socket.close();
}
