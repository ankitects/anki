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
async function selectOption(selector, label) {
  await evaluate(`(() => {
    const select = document.querySelector(${JSON.stringify(selector)});
    const option = [...select.options].find((candidate) => candidate.textContent === ${JSON.stringify(label)});
    if (!option) throw new Error("Option not found: ${label}");
    Object.getOwnPropertyDescriptor(HTMLSelectElement.prototype, "value").set.call(select, option.value);
    select.dispatchEvent(new Event("change", { bubbles: true }));
  })()`);
}
async function openDeck(label) {
  await until(`[...document.querySelectorAll(".deck-row")].some((row) => row.textContent.includes(${JSON.stringify(label)}))`);
  await evaluate(`[...document.querySelectorAll(".deck-row")].find((row) => row.textContent.includes(${JSON.stringify(label)})).click()`);
}
async function createDeck(name) {
  await evaluate('document.querySelector("button[aria-label=\\"Add deck\\"]").click()');
  await until('document.querySelector("#deck-name")');
  await setValue("#deck-name", name);
  await evaluate('document.querySelector(".form-panel").requestSubmit()');
  await until(`document.body.innerText.includes(${JSON.stringify(name)}) && document.body.innerText.includes("Study now")`);
}
async function addBasicCard(front, back) {
  await evaluate('document.querySelector("button[aria-label=\\"Add card\\"]").click()');
  await until('document.querySelector("#note-field-0")');
  await setValue("#note-field-0", front);
  await setValue("#note-field-1", back);
  await evaluate('document.querySelector(".form-panel").requestSubmit()');
  await until('document.body.innerText.includes("card") && document.body.innerText.includes("total")');
}

try {
  await until('document.querySelector(".storage-banner")');

  await createDeck("Deck management test");
  await addBasicCard("Move me", "Destination answer");
  await evaluate('document.querySelector("button[aria-label=\\"Back\\"]").click()');
  await until('document.querySelector(".deck-list")');

  await createDeck("Move destination");
  await evaluate('document.querySelector("button[aria-label=\\"Back\\"]").click()');
  await until('document.querySelector(".deck-list")');

  await openDeck("Deck management test");
  await click("Manage");
  await until('document.querySelector("#subdeck-name")');
  await setValue("#subdeck-name", "Child");
  await evaluate('document.querySelector("#subdeck-name").closest("form").requestSubmit()');
  await until('document.body.innerText.includes("Deck management test::Child") && document.body.innerText.includes("Study now")');

  await click("Manage");
  await until('document.querySelector("#manage-deck-name")');
  await setValue("#manage-deck-name", "Renamed child");
  await evaluate('document.querySelector("#manage-deck-name").closest("form").requestSubmit()');
  await until('document.body.innerText.includes("Deck management test::Renamed child") && document.body.innerText.includes("Study now")');
  await addBasicCard("Child delete me", "This should be removed with its parent");

  await evaluate('document.querySelector("button[aria-label=\\"Back\\"]").click()');
  await until('document.querySelector(".deck-list")');
  await click("Browse");
  await until('document.querySelector("#browser-query")');
  await setValue("#browser-query", "Move me");
  await evaluate('document.querySelector(".browser-search").requestSubmit()');
  await until('document.querySelectorAll(".browser-note-row").length === 1');
  await evaluate('document.querySelector(".browser-note-row").click()');
  await until('document.querySelector("#browser-field-0")');
  await setValue("#browser-tags", "edited deck-management");
  await evaluate('document.querySelector(".browser-editor .form-panel").requestSubmit()');
  await until('document.body.innerText.includes("Note saved.")');

  await setValue("#browser-query", "Move me");
  await evaluate('document.querySelector(".browser-search").requestSubmit()');
  await until('document.querySelectorAll(".browser-note-row").length === 1');
  assert.match(await evaluate("document.body.innerText"), /deck-management/);
  await evaluate('document.querySelector(".browser-note-row").click()');
  await until('document.querySelector(".browser-card-actions select")');
  await selectOption(".browser-card-actions select", "Move destination");
  await until('document.querySelector(".browser-card").textContent.includes("Move destination")');

  await click("Suspend");
  await until('document.body.innerText.includes("Suspended")');
  await click("Resume");
  await until('document.body.innerText.includes("New")');
  await click("Bury");
  await until('document.body.innerText.includes("Buried")');
  await click("Unbury");
  await until('document.body.innerText.includes("New")');

  await click("Decks");
  await until('document.querySelector(".deck-list")');
  assert.match(await evaluate('[...document.querySelectorAll(".deck-row")].find((row) => row.textContent.includes("Move destination")).textContent'), /1/);

  await openDeck("Deck management test");
  await click("Manage");
  await evaluate("window.confirm = () => true");
  await click("Delete deck");
  await until('document.querySelector(".deck-list")');
  assert.equal(await evaluate('[...document.querySelectorAll(".deck-row")].some((row) => row.textContent.includes("Deck management test"))'), false);
  assert.equal(await evaluate('[...document.querySelectorAll(".deck-row")].some((row) => row.textContent.includes("Renamed child"))'), false);

  await click("Browse");
  await until('document.querySelector("#browser-query")');
  await setValue("#browser-query", "Child delete me");
  await evaluate('document.querySelector(".browser-search").requestSubmit()');
  await until('document.querySelectorAll(".browser-note-row").length === 0');
  await setValue("#browser-query", "deck-management");
  await evaluate('document.querySelector(".browser-search").requestSubmit()');
  await until('document.querySelectorAll(".browser-note-row").length === 1');
  assert.match(await evaluate("document.body.innerText"), /Move destination/);

  console.log(JSON.stringify({ tags: true, subdecks: true, rename: true, move: true, recursiveDelete: true, suspend: true, bury: true }));
} finally {
  socket.close();
}
