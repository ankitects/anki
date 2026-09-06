// Run only against a fresh Chrome profile and a dedicated test origin.
// Usage: npm run test:browse-browser -- <CDP port> <PWA URL>
import assert from "node:assert/strict";

const [port = "9234", origin = "http://127.0.0.1:3013/"] = process.argv.slice(2);
const targets = await (await fetch(`http://127.0.0.1:${port}/json`)).json();
const target = targets.find((candidate) => candidate.type === "page" && candidate.url === origin);
assert.ok(target, `Open ${origin} in a fresh Chrome profile with --remote-debugging-port=${port}`);
const socket = new WebSocket(target.webSocketDebuggerUrl);
await new Promise((resolve, reject) => { socket.onopen = resolve; socket.onerror = reject; });
let id = 0;
const pending = new Map();
socket.onmessage = (event) => {
  const response = JSON.parse(event.data);
  const handler = pending.get(response.id);
  if (handler) { pending.delete(response.id); handler(response); }
};
function command(method, params = {}) {
  const requestId = ++id;
  return new Promise((resolve, reject) => {
    pending.set(requestId, (reply) => reply.error ? reject(new Error(JSON.stringify(reply.error))) : resolve(reply.result));
    socket.send(JSON.stringify({ id: requestId, method, params }));
  });
}
async function evaluate(expression) {
  const reply = await command("Runtime.evaluate", { expression, awaitPromise: true, returnByValue: true });
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
async function click(label) {
  await until(`[...document.querySelectorAll("button")].some((button) => button.textContent.trim() === ${JSON.stringify(label)} && !button.disabled)`);
  await evaluate(`[...document.querySelectorAll("button")].find((button) => button.textContent.trim() === ${JSON.stringify(label)} && !button.disabled).click()`);
}
async function setValue(selector, value) {
  await evaluate(`(() => {
    const input = document.querySelector(${JSON.stringify(selector)});
    const prototype = input instanceof HTMLSelectElement ? HTMLSelectElement.prototype
      : input instanceof HTMLTextAreaElement ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;
    Object.getOwnPropertyDescriptor(prototype, "value").set.call(input, ${JSON.stringify(value)});
    input.dispatchEvent(new Event(input instanceof HTMLSelectElement ? "change" : "input", { bubbles: true }));
  })()`);
}

try {
  await until('document.querySelector(".storage-banner")');
  await evaluate('document.querySelector("button[aria-label=\\"Add deck\\"]").click()');
  await until('document.querySelector("#deck-name")');
  await setValue("#deck-name", "Browser feature test");
  await evaluate('document.querySelector(".form-panel").requestSubmit()');
  await until('document.body.innerText.includes("Browser feature test") && document.body.innerText.includes("Study now")');
  await evaluate('document.querySelector("button[aria-label=\\"Add card\\"]").click()');
  await until('document.querySelector("#note-field-0")');
  await setValue("#note-field-0", "Browse before edit");
  await setValue("#note-field-1", "Back before edit");
  await evaluate('document.querySelector(".form-panel").requestSubmit()');
  await until('document.body.innerText.includes("1 card total")');

  await evaluate('document.querySelector("button[aria-label=\\"Back\\"]").click()');
  await until('document.querySelector(".deck-list")');
  await click("Browse");
  await until('document.querySelector("#browser-query")');
  await setValue("#browser-query", "Browse before edit");
  await evaluate('document.querySelector(".browser-search").requestSubmit()');
  await until('document.querySelectorAll(".browser-note-row").length === 1');
  await evaluate('document.querySelector(".browser-note-row").click()');
  await until('document.querySelector("#browser-field-0")');
  await setValue("#browser-field-0", "Browse after edit");
  await setValue("#browser-tags", "edited browser-test");
  await evaluate('document.querySelector(".browser-editor .form-panel").requestSubmit()');
  await until('document.body.innerText.includes("Note saved.")');

  await setValue("#browser-query", "Browse after edit");
  await evaluate('document.querySelector(".browser-search").requestSubmit()');
  await until('document.querySelectorAll(".browser-note-row").length === 1');
  assert.match(await evaluate("document.body.innerText"), /edited/);
  await evaluate('document.querySelector(".browser-note-row").click()');
  await click("Suspend");
  await until('document.body.innerText.includes("Suspended")');
  await click("Resume");
  await until('document.body.innerText.includes("New")');
  await click("Bury");
  await until('document.body.innerText.includes("Buried")');
  await click("Unbury");
  await until('document.body.innerText.includes("New")');

  await evaluate("window.confirm = () => true");
  await click("Delete note");
  await until('document.body.innerText.includes("Note deleted.")');
  assert.equal(await evaluate('document.querySelectorAll(".browser-note-row").length'), 0);
  await click("Decks");
  await until('document.querySelector(".deck-list")');
  assert.match(await evaluate('[...document.querySelectorAll(".deck-row")].find((row) => row.textContent.includes("Browser feature test")).textContent'), /000/);
  console.log(JSON.stringify({ search: true, edit: true, tags: true, suspend: true, bury: true, delete: true }));
} finally {
  socket.close();
}
