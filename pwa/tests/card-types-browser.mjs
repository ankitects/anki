// Run only against a fresh Chrome profile and a dedicated test origin.
// Usage: npm run test:card-types-browser -- <CDP port> <PWA URL>
import assert from "node:assert/strict";

const [port = "9231", origin = "http://127.0.0.1:3002/"] = process.argv.slice(2);
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
  await until(`[...document.querySelectorAll('button')].some((button) => button.textContent.trim().endsWith(${JSON.stringify(label)}) && !button.disabled)`);
  await evaluate(`[...document.querySelectorAll('button')].find((button) => button.textContent.trim().endsWith(${JSON.stringify(label)})).click()`);
}
async function setValue(selector, value) {
  await evaluate(`(() => {
    const input = document.querySelector(${JSON.stringify(selector)});
    const descriptor = Object.getOwnPropertyDescriptor(input instanceof HTMLSelectElement ? HTMLSelectElement.prototype
      : input instanceof HTMLTextAreaElement ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype, 'value');
    descriptor.set.call(input, ${JSON.stringify(value)}); input.dispatchEvent(new Event(input instanceof HTMLSelectElement ? 'change' : 'input', { bubbles: true }));
  })()`);
}
async function createDeck(name) {
  await evaluate('document.querySelector("button[aria-label=\\"Add deck\\"]").click()');
  await until('document.querySelector("#deck-name")');
  await setValue("#deck-name", name);
  await evaluate('document.querySelector("form").requestSubmit()');
  await until(`document.body.innerText.includes(${JSON.stringify(name)}) && document.body.innerText.includes('Study now')`);
}
async function addScreen(notetype) {
  await evaluate('document.querySelector("button[aria-label=\\"Add card\\"]").click()');
  await until('document.querySelector("#note-type")');
  const value = await evaluate(`[...document.querySelector('#note-type').options].find((option) => option.textContent === ${JSON.stringify(notetype)})?.value`);
  assert.ok(value, `Missing note type: ${notetype}`);
  await setValue("#note-type", value);
}
async function fillFields(values) {
  for (let index = 0; index < values.length; index++) await setValue(`#note-field-${index}`, values[index]);
  await evaluate('document.querySelector("form").requestSubmit()');
  await until('document.body.innerText.includes("Study now")');
}
async function backToDecks() {
  await evaluate('document.querySelector("button[aria-label=\\"Back\\"]").click()');
  await until('document.querySelector("button[aria-label=\\"Add deck\\"]")');
}

try {
  await until('document.querySelector(".storage-banner")');
  assert.match(await evaluate("document.body.innerText"), /Stored locally on this device/);
  assert.equal(await evaluate('[...document.querySelectorAll(".deck-row")].some((row) => /card types browser test/i.test(row.textContent))'), false,
    "Use a fresh test profile/origin, not an existing collection.");

  await createDeck("Reverse card types browser test");
  await addScreen("Basic (and reversed card)");
  const choices = await evaluate('[...document.querySelector("#note-type").options].map((option) => option.textContent)');
  for (const expected of ["Basic", "Basic (and reversed card)", "Basic (optional reversed card)",
    "Basic (type in the answer)", "Cloze", "Image Occlusion"]) assert.ok(choices.includes(expected), expected);
  await fillFields(["front", "back"]);
  assert.match(await evaluate("document.body.innerText"), /2 cards total/);
  await addScreen("Basic (optional reversed card)");
  await fillFields(["front only", "back only", ""]);
  assert.match(await evaluate("document.body.innerText"), /3 cards total/);
  await addScreen("Basic (optional reversed card)");
  await fillFields(["optional front", "optional back", "yes"]);
  assert.match(await evaluate("document.body.innerText"), /5 cards total/);
  await backToDecks();

  await createDeck("Typing card types browser test");
  await addScreen("Basic (type in the answer)");
  await fillFields(["Capital of France?", "Paris"]);
  await click("Study now");
  await until('document.querySelector("#typed-answer")');
  assert.doesNotMatch(await evaluate('document.querySelector("iframe").srcdoc'), /Paris/);
  await setValue("#typed-answer", "London");
  await click("Show answer");
  await until('document.querySelector(".typed-answer-result")');
  assert.match(await evaluate("document.body.innerText"), /Your answer\s*London\s*Correct answer\s*Paris/);
  assert.equal(await evaluate('document.querySelector(".typed-answer-result").classList.contains("incorrect")'), true);
  await click("Easy");
  await click("Back to deck");
  await backToDecks();

  await createDeck("Occlusion card types browser test");
  await addScreen("Image Occlusion");
  await evaluate(`(() => {
    const svg = '<svg xmlns="http://www.w3.org/2000/svg" width="400" height="240"><rect width="400" height="240" fill="white"/><circle cx="200" cy="120" r="60" fill="blue"/></svg>';
    const transfer = new DataTransfer(); transfer.items.add(new File([svg], 'diagram.svg', { type: 'image/svg+xml' }));
    const input = document.querySelector('#occlusion-image'); input.files = transfer.files;
    input.dispatchEvent(new Event('change', { bubbles: true }));
  })()`);
  await until('document.querySelector(".io-drawing-layer") && document.querySelector(".io-editor-frame img").complete');
  const bounds = await evaluate(`(() => { const box = document.querySelector('.io-drawing-layer').getBoundingClientRect();
    return { x: box.x, y: box.y, width: box.width, height: box.height }; })()`);
  await command("Input.dispatchMouseEvent", { type: "mousePressed", x: bounds.x + bounds.width * .35, y: bounds.y + bounds.height * .3, button: "left", clickCount: 1 });
  await command("Input.dispatchMouseEvent", { type: "mouseMoved", x: bounds.x + bounds.width * .65, y: bounds.y + bounds.height * .7, button: "left" });
  await command("Input.dispatchMouseEvent", { type: "mouseReleased", x: bounds.x + bounds.width * .65, y: bounds.y + bounds.height * .7, button: "left", clickCount: 1 });
  await until('document.querySelectorAll(".io-editor-mask").length === 1');
  await setValue("#occlusion-header", "Diagram header");
  await setValue("#occlusion-extra", "Diagram details");
  await evaluate('document.querySelector("form").requestSubmit()');
  await until('document.body.innerText.includes("1 card total")');
  await command("Page.reload");
  await until('document.querySelector(".storage-banner")');
  await evaluate('[...document.querySelectorAll(".deck-row")].find((row) => row.textContent.includes("Occlusion card types browser test")).click()');
  await click("Study now");
  await until('document.querySelector("iframe")');
  let source = await evaluate('document.querySelector("iframe").srcdoc');
  assert.match(source, /Diagram header/); assert.match(source, /io-active/); assert.match(source, /data:image\/svg\+xml;base64,/);
  await click("Show answer");
  source = await evaluate('document.querySelector("iframe").srcdoc');
  assert.match(source, /io-highlight/); assert.match(source, /Diagram details/);

  console.log(JSON.stringify({ stockChoices: 6, reversedCards: 2, optionalReverse: true,
    typeAnswerInput: true, typeAnswerComparison: true, imageMaskEditor: true, imageOcclusionReview: true }));
} finally {
  socket.close();
}
