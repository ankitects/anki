const fs = require("fs");
const process = require("process");
const path = require("path");
const worker = require("@bazel/worker");
const svelte = require("svelte/compiler.js");
const svelte2tsx = require("svelte2tsx");
const preprocess = require("svelte-preprocess");
const ts = require("typescript");

const tsOptions = {
    jsx: "preserve",
    declaration: true,
    emitDeclarationOnly: true,
    skipLibCheck: true,
};
const tsHost = ts.createCompilerHost(tsOptions);

function writeFile(file, data) {
    return new Promise((resolve, reject) => {
        fs.writeFile(file, data, (err) => {
            if (err) {
                reject(err);
                return;
            }
            resolve();
        });
    });
}

function readFile(file) {
    return new Promise((resolve, reject) => {
        fs.readFile(file, "utf8", (err, data) => {
            if (err) {
                reject(err);
                return;
            }
            resolve(data);
        });
    });
}

function buildDeclarations(svelteTsFile, shims) {
    const createdFiles = {}; //test2
    tsHost.writeFile = (fileName, contents) => (createdFiles[fileName] = contents);
    const program = ts.createProgram([svelteTsFile, ...shims], tsOptions, tsHost);
    program.emit();
    const dtsSource = createdFiles[svelteTsFile.replace(".ts", ".d.ts")];
    return dtsSource;
}

async function writeDts(tsPath, dtsPath, shims) {
    const dtsSource = buildDeclarations(tsPath, shims);
    await writeFile(dtsPath, dtsSource);
}

async function writeTs(svelteSource, sveltePath, tsPath) {
    let tsSource = svelte2tsx(svelteSource, {
        filename: sveltePath,
        strictMode: true,
        isTsFile: true,
    });
    let codeLines = tsSource.code.split("\n");
    // replace the "///<reference types="svelte" />" with a line
    // turning off checking, as we'll use svelte-check for that
    codeLines[0] = "// @ts-nocheck";
    await writeFile(tsPath, codeLines.join("\n"));
}

//test
async function writeJs(source, inputFilename, outputPath) {
    const preprocessOptions = preprocess({});
    preprocessOptions.filename = inputFilename;

    try {
        const processed = await svelte.preprocess(source, preprocessOptions);
        const result = svelte.compile(processed.toString(), {
            format: "esm",
            generate: "dom",
            filename: outputPath,
        });
        // warnings are an error
        if (result.warnings.length > 0) {
            console.log(`warnings during compile: ${result.warnings}`);
            return;
        }
        const outputSource = result.js.code;
        await writeFile(outputPath, outputSource);
    } catch (err) {
        console.log(`compile failed: ${err}`);
        return;
    }
}

async function compileSvelte(args) {
    const [sveltePath, mjsPath, dtsPath, tempTsPath, ...shims] = args;
    const svelteSource = await readFile(sveltePath);

    await writeTs(svelteSource, sveltePath, tempTsPath);
    await writeDts(tempTsPath, dtsPath, shims);
    await writeJs(svelteSource, sveltePath, mjsPath);

    return true;
}

function main(args) {
    if (worker.runAsWorker(process.argv)) {
        worker.log("Svelte running as a Bazel worker");
        worker.runWorkerLoop(compileSvelte);
    } else {
        const paramFile = process.argv[2].replace(/^@/, "");
        const commandLineArgs = fs.readFileSync(paramFile, "utf-8").trim().split("\n");
        console.log("Svelte running as a standalone process");
        compileSvelte(commandLineArgs);
    }
}

if (require.main === module) {
    main(process.argv.slice(2));
    process.exitCode = 0;
}
