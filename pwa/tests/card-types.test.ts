import assert from "node:assert/strict";
import test from "node:test";

import { clozeOrdinals, parseImageOcclusions, renderAnkiCard } from "../src/lib/anki/template";
import type { AnkiNotetype } from "../src/lib/anki/template";

const css = ".card { color: black; }";
const field = (name: string, ord: number, tag?: number) => ({ name, ord, ...(tag === undefined ? {} : { tag }) });

test("forward and reverse templates render both card directions", () => {
  const model: AnkiNotetype = {
    id: 1, name: "Basic (and reversed card)", type: 0, css,
    flds: [field("Front", 0), field("Back", 1)],
    tmpls: [
      { name: "Card 1", ord: 0, qfmt: "{{Front}}", afmt: "{{FrontSide}}<hr>{{Back}}" },
      { name: "Card 2", ord: 1, qfmt: "{{Back}}", afmt: "{{FrontSide}}<hr>{{Front}}" }
    ]
  };
  const forward = renderAnkiCard(model, ["Question", "Answer"], 0);
  const reverse = renderAnkiCard(model, ["Question", "Answer"], 1);
  assert.equal(forward.questionHtml, "Question");
  assert.match(forward.answerHtml, /Question<hr>Answer/);
  assert.equal(reverse.questionHtml, "Answer");
  assert.match(reverse.answerHtml, /Answer<hr>Question/);
});

test("optional reverse template honors the Add Reverse field", () => {
  const model: AnkiNotetype = {
    id: 2, name: "Basic (optional reversed card)", type: 0, css,
    flds: [field("Front", 0), field("Back", 1), field("Add Reverse", 2)],
    tmpls: [
      { name: "Card 1", ord: 0, qfmt: "{{Front}}", afmt: "{{Back}}" },
      { name: "Card 2", ord: 1, qfmt: "{{#Add Reverse}}{{Back}}{{/Add Reverse}}", afmt: "{{Front}}" }
    ]
  };
  assert.equal(renderAnkiCard(model, ["Q", "A", ""], 1).questionHtml, "");
  assert.equal(renderAnkiCard(model, ["Q", "A", "yes"], 1).questionHtml, "A");
});

test("type filter produces an external typed-answer prompt without leaking the answer", () => {
  const model: AnkiNotetype = {
    id: 3, name: "Basic (type in the answer)", type: 0, css,
    flds: [field("Front", 0), field("Back", 1)],
    tmpls: [{ name: "Card 1", ord: 0, qfmt: "{{Front}} {{type:Back}}", afmt: "{{Front}}<hr>{{type:Back}}" }]
  };
  const rendered = renderAnkiCard(model, ["Capital of France?", "<b>Paris</b> &amp; Lyon"], 0);
  assert.deepEqual(rendered.typedAnswer, { field: "Back", correct: "Paris & Lyon" });
  assert.match(rendered.questionHtml, /Capital of France/);
  assert.doesNotMatch(rendered.questionHtml, /Paris/);
  assert.match(rendered.answerHtml, /Paris &amp; Lyon/);
});

test("image occlusion parses Anki rectangles and renders active/inactive masks", () => {
  const model: AnkiNotetype = {
    id: 4, name: "Image Occlusion", type: 1, originalStockKind: 6, css,
    flds: [field("Occlusion", 0, 0), field("Image", 1, 1), field("Header", 2, 2),
      field("Back Extra", 3, 3), field("Comments", 4, 4)],
    tmpls: [{ name: "Image Occlusion", ord: 0, qfmt: "ignored", afmt: "ignored" }]
  };
  const occlusions = "{{c1::image-occlusion:rect:left=.1:top=.2:width=.3:height=.4:oi=1}}<br>"
    + "{{c2::image-occlusion:ellipse:left=.5:top=.6:rx=.1:ry=.15}}<br>";
  assert.deepEqual(clozeOrdinals([occlusions]), [0, 1]);
  assert.equal(parseImageOcclusions(occlusions).length, 2);
  const first = renderAnkiCard(model, [occlusions, '<img src="diagram.png">', "Anatomy", "Details", ""], 0);
  assert.match(first.questionHtml, /Anatomy/);
  assert.match(first.questionHtml, /diagram\.png/);
  assert.match(first.questionHtml, /left:10%;top:20%;width:30%;height:40%/);
  assert.match(first.questionHtml, /io-active/);
  assert.doesNotMatch(first.questionHtml, /border-radius:50%/);
  assert.match(first.answerHtml, /io-highlight/);
  assert.match(first.answerHtml, /Details/);
  const second = renderAnkiCard(model, [occlusions, '<img src="diagram.png">', "", "", ""], 1);
  assert.match(second.questionHtml, /border-radius:50%/);
  assert.match(second.questionHtml, /io-inactive/);
});

test("malformed image occlusion coordinates are clamped and cannot inject CSS", () => {
  const model: AnkiNotetype = {
    id: 5, name: "Image Occlusion", type: 1, originalStockKind: 6, css,
    flds: [field("Occlusion", 0), field("Image", 1), field("Header", 2), field("Back Extra", 3)],
    tmpls: [{ name: "Image Occlusion", qfmt: "", afmt: "" }]
  };
  const rendered = renderAnkiCard(model,
    ["{{c1::image-occlusion:rect:left=2;background\:red:top=-2:width=bad:height=.5}}", "<img src=x>", "", ""], 0);
  assert.match(rendered.questionHtml, /left:0%;top:0%;width:0%;height:50%/);
  assert.doesNotMatch(rendered.questionHtml, /background:red/);
});
