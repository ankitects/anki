const fs = require("fs");
const process = require("process");
const path = require("path");

const input = process.argv[2];
const outputJs = process.argv[3];
const temp = process.argv[4];


const svelte = require("svelte/compiler");

const source = fs.readFileSync(input, "utf8");

const preprocessOptions = require("svelte-preprocess")({});
preprocessOptions.filename = input;

const svelte2tsx = require("svelte2tsx");

let tsoutput = svelte2tsx(source, {
  filename: input,
  strictMode: true,
  isTsFile: true,
});
let codeLines = tsoutput.code.split("\n");
// replace the "///<reference types="svelte" />" with a line
// turning off checking, as we'll use svelte-check for that
codeLines[0] = "// @ts-nocheck";
const outputBase = path.basename(outputJs.replace(".mjs", ".tsx"));
const outputTs = path.join(temp, outputBase);
fs.writeFileSync(outputTs, codeLines.join("\n"));

svelte.preprocess(source, preprocessOptions).then(
  (processed) => {
    let result;
    try {
      result = svelte.compile(processed.toString(), {
        format: "esm",
        generate: "dom",
        filename: outputJs,
      });
    } catch (err) {
      console.log(`compile failed: ${err}`);
      return;
    }
    if (result.warnings.length > 0) {
      console.log(`warnings during compile: ${result.warnings}`);
      return;
    }

    let code = result.js.code;
    fs.writeFileSync(outputJs, code);
  },
  (error) => {
    console.log(`preprocess failed: ${error}`);
  }
);
