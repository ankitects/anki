// Run against a dedicated test origin and fresh Chrome profile; never a user's collection.
// Usage: npm run test:browser -- <CDP port> <PWA URL> [legacy|modern]
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { createHash } from "node:crypto";
import { zstdCompressSync } from "node:zlib";
import sqlite3InitModule from "@sqlite.org/sqlite-wasm";
import { strToU8, zipSync } from "fflate";

const [port = "9230", origin = "http://127.0.0.1:3002/", format = "legacy"] = process.argv.slice(2);
assert.ok(["legacy", "modern"].includes(format));
const targets = await (await fetch(`http://127.0.0.1:${port}/json`)).json();
const target = targets.find((target) => target.type === "page" && target.url === origin);
assert.ok(target, `Open ${origin} in a fresh Chrome profile with --remote-debugging-port=${port}`);
const socket = new WebSocket(target.webSocketDebuggerUrl);
await new Promise((resolve, reject) => { socket.onopen = resolve; socket.onerror = reject; });
let requestId = 0;
const pending = new Map();
socket.onmessage = (event) => {
  const response = JSON.parse(event.data);
  const handler = pending.get(response.id);
  if (handler) { pending.delete(response.id); handler(response); }
};
function command(method, params = {}) {
  const id = ++requestId;
  return new Promise((resolve, reject) => {
    pending.set(id, (reply) => reply.error ? reject(new Error(JSON.stringify(reply.error))) : resolve(reply.result));
    socket.send(JSON.stringify({ id, method, params }));
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
async function button(label) {
  await until(`[...document.querySelectorAll('button')].some((button) => button.textContent.trim() === ${JSON.stringify(label)} && !button.disabled)`);
  await evaluate(`(() => {
    const button = [...document.querySelectorAll('button')].find((button) => button.textContent.trim() === ${JSON.stringify(label)});
    if (!button || button.disabled) throw new Error('Missing/disabled button: ' + ${JSON.stringify(label)});
    button.click();
  })()`);
}

const sqlite = await sqlite3InitModule();
const db = new sqlite.oo1.DB(":memory:", "c");
db.exec(readFileSync(new URL("../../rslib/src/storage/schema11.sql", import.meta.url), "utf8"));
const model = { id: 100, name: "Import smoke", type: 0, sortf: 0,
  flds: [{ name: "Front", ord: 0 }, { name: "Back", ord: 1 }],
  tmpls: [{ name: "Card 1", ord: 0, qfmt: "{{Front}}", afmt: "{{FrontSide}}<hr>{{Back}}" }],
  css: '.card { color: rgb(10, 80, 120); }' };
db.exec({ sql: "UPDATE col SET crt = ?, ver = 11, models = ?, decks = ?",
  bind: [Math.floor(Date.now() / 1000), JSON.stringify({ 100: model }), JSON.stringify({ 200: { id: 200, name: "APKG browser test", dyn: 0, conf: 1 } })] });
db.exec({ sql: "INSERT INTO notes VALUES (10, 'browser-smoke', 100, 1, -1, ' smoke ', ?, 'Hello import', 1, 0, '')",
  bind: ['Hello import\u001fImported answer <img src=dot.svg><div style="background:url(\'dot.svg\')">Background</div>[sound:tone.wav]'] });
db.exec("INSERT INTO cards VALUES (20, 10, 200, 0, 1, -1, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, '')");
const wave = new Uint8Array(46);
const view = new DataView(wave.buffer);
wave.set(strToU8("RIFF")); view.setUint32(4, 38, true); wave.set(strToU8("WAVEfmt "), 8);
view.setUint32(16, 16, true); view.setUint16(20, 1, true); view.setUint16(22, 1, true);
view.setUint32(24, 8000, true); view.setUint32(28, 16000, true); view.setUint16(32, 2, true); view.setUint16(34, 16, true);
wave.set(strToU8("data"), 36); view.setUint32(40, 2, true);
const entries = { "collection.anki21": sqlite.capi.sqlite3_js_db_export(db.pointer),
  media: strToU8('{"0":"dot.svg","1":"tone.wav"}'),
  "0": strToU8('<svg xmlns="http://www.w3.org/2000/svg" width="20" height="20"><circle cx="10" cy="10" r="8" fill="red"/></svg>'), "1": wave };
if (format === "modern") {
  const concat = (...arrays) => new Uint8Array(Buffer.concat(arrays));
  const variable = (value) => {
    const bytes = [];
    do { const low = value % 128; value = Math.floor(value / 128); bytes.push(low | (value ? 128 : 0)); } while (value);
    return Uint8Array.from(bytes);
  };
  const uint = (field, value) => concat(variable(field * 8), variable(value));
  const binary = (field, value) => concat(variable(field * 8 + 2), variable(value.length), value);
  const string = (field, value) => binary(field, strToU8(value));
  db.exec(`UPDATE col SET ver = 18, models = '', decks = '';
    CREATE TABLE notetypes (id INTEGER PRIMARY KEY, name TEXT, config BLOB);
    CREATE TABLE fields (ntid INTEGER, ord INTEGER, name TEXT, config BLOB, PRIMARY KEY(ntid, ord)) WITHOUT ROWID;
    CREATE TABLE templates (ntid INTEGER, ord INTEGER, name TEXT, config BLOB, PRIMARY KEY(ntid, ord)) WITHOUT ROWID;
    CREATE TABLE decks (id INTEGER PRIMARY KEY, name TEXT, common BLOB, kind BLOB);`);
  db.exec({ sql: "INSERT INTO notetypes VALUES (100, 'Import smoke', ?)", bind: [string(3, model.css)] });
  for (const [index, name] of ["Front", "Back"].entries()) {
    db.exec({ sql: "INSERT INTO fields VALUES (100, ?, ?, ?)", bind: [index, name, new Uint8Array()] });
  }
  db.exec({ sql: "INSERT INTO templates VALUES (100, 0, 'Card 1', ?)",
    bind: [concat(string(1, model.tmpls[0].qfmt), string(2, model.tmpls[0].afmt))] });
  db.exec({ sql: "INSERT INTO decks VALUES (200, 'APKG browser test', ?, ?)", bind: [new Uint8Array(), binary(1, uint(1, 1))] });
  entries.meta = uint(1, 3);
  entries["collection.anki21b"] = zstdCompressSync(sqlite.capi.sqlite3_js_db_export(db.pointer));
  entries["collection.anki2"] = strToU8("Compatibility placeholder: never import this");
  delete entries["collection.anki21"];
  entries.media = zstdCompressSync(concat(...["dot.svg", "tone.wav"].map((name, index) => {
    const content = entries[String(index)];
    const entry = binary(1, concat(string(1, name), uint(2, content.length), binary(3, createHash("sha1").update(content).digest())));
    entries[String(index)] = zstdCompressSync(content);
    return entry;
  })));
}
const bytes = zipSync(entries);
db.close();

async function upload(bytes, name = "smoke.apkg") {
  await until('document.querySelector("#apkg-file")');
  await evaluate(`(() => {
    const bytes = Uint8Array.from(atob(${JSON.stringify(Buffer.from(bytes).toString("base64"))}), (character) => character.charCodeAt(0));
    const transfer = new DataTransfer(); transfer.items.add(new File([bytes], ${JSON.stringify(name)}));
    const input = document.querySelector('#apkg-file'); input.files = transfer.files;
    input.dispatchEvent(new Event('change', { bubbles: true }));
  })()`);
  await until('!document.querySelector("button[type=submit]").disabled');
  await button("Import deck");
}

try {
  await until('document.querySelector(".storage-banner")');
  assert.match(await evaluate("document.body.innerText"), /Stored locally on this device/);
  assert.equal(await evaluate('[...document.querySelectorAll(".deck-row")].some((row) => row.textContent.includes("APKG browser test"))'), false,
    "Use a fresh test profile/origin, not an existing collection.");
  await button("Import");
  await upload(bytes);
  await until('document.body.innerText.includes("Import complete")');
  assert.match(await evaluate("document.body.innerText"), /1 notes · 1 cards · 2 new media files/);
  await button("Back to decks");
  await button("Import");
  await upload(bytes);
  await until('document.body.innerText.includes("No new notes to import")');
  assert.match(await evaluate("document.body.innerText"), /1 existing notes skipped/);
  await button("Back to decks");
  await button("Import");
  await upload(strToU8("broken package"));
  await until('document.querySelector("[role=alert]")');
  await evaluate('document.querySelector("button[aria-label=Back]").click()');
  await until('document.querySelector(".deck-row")');
  await evaluate("navigator.serviceWorker.ready.then(() => true)");
  await command("Page.reload");
  await until('document.querySelector(".storage-banner")');
  assert.match(await evaluate("document.body.innerText"), /Stored locally on this device/);
  await evaluate('[...document.querySelectorAll(".deck-row")].find((row) => row.textContent.includes("APKG browser test")).click()');
  await button("Study now");
  await until('document.querySelector("iframe")');
  assert.match(await evaluate('document.querySelector("iframe").srcdoc'), /Hello import/);
  await button("Show answer");
  let source = await evaluate('document.querySelector("iframe").srcdoc');
  assert.match(source, /Imported answer/); assert.match(source, /data:image\/svg\+xml;base64,/); assert.match(source, /data:audio\/wav;base64,/);
  assert.match(source, /style="background:url\(data:image\/svg\+xml;base64,[^"]+\)"/);
  assert.equal(await evaluate('document.querySelector("iframe").getAttribute("sandbox")'), "");
  await command("Network.enable");
  await command("Network.emulateNetworkConditions", { offline: true, latency: 0, downloadThroughput: 0, uploadThroughput: 0 });
  await command("Page.reload");
  await until('document.querySelector(".storage-banner")');
  await evaluate('[...document.querySelectorAll(".deck-row")].find((row) => row.textContent.includes("APKG browser test")).click()');
  await button("Study now");
  await until('document.querySelector("iframe")');
  await button("Show answer");
  source = await evaluate('document.querySelector("iframe").srcdoc');
  assert.match(source, /data:image\/svg\+xml;base64,/); assert.match(source, /data:audio\/wav;base64,/);
  await evaluate('[...document.querySelectorAll("button")].find((button) => button.textContent.includes("Easy")).click()');
  await until('!document.querySelector("iframe")');
  console.log(JSON.stringify({ format, upload: true, duplicatesSkipped: true, invalidPackageReported: true,
    persistentAfterReload: true, importedTemplates: true, image: true, audio: true, offlineReloadAndReview: true }));
} finally {
  await command("Network.emulateNetworkConditions", { offline: false, latency: 0, downloadThroughput: -1, uploadThroughput: -1 }).catch(() => {});
  socket.close();
}
