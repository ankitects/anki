const fs = require("fs");
const worker = require("@bazel/worker");
const svelte2tsx = require("svelte2tsx");
const preprocess = require("svelte-preprocess");
import * as ts from "typescript";
import * as svelte from "svelte/compiler";

let parsedCommandLine: ts.ParsedCommandLine = {
    fileNames: [],
    errors: [],
    options: {
        jsx: ts.JsxEmit.Preserve,
        declaration: true,
        emitDeclarationOnly: true,
        skipLibCheck: true,
    },
};

let tsText = "";

// largely taken from https://github.com/Asana/bazeltsc/blob/7dfa0ba2bd5eb9ee556e146df35cf793fad2d2c3/src/bazeltsc.ts (MIT)
const languageServiceHost: ts.LanguageServiceHost = {
    getCompilationSettings: (): ts.CompilerOptions => parsedCommandLine.options,
    getNewLine: () => ts.sys.newLine,
    getScriptFileNames: (): string[] => parsedCommandLine.fileNames,
    getScriptVersion: (fileName: string): string => {
        if (fileName == parsedCommandLine.fileNames[0]) {
            return require("crypto").createHash("md5").update(tsText).digest("hex");
        } else {
            // If the file's size or modified-timestamp changed, it's a different version.
            return (
                ts.sys.getFileSize!(fileName) +
                ":" +
                ts.sys.getModifiedTime!(fileName)!.getTime()
            );
        }
    },
    getScriptSnapshot: (fileName: string): ts.IScriptSnapshot | undefined => {
        let text;
        if (fileName == parsedCommandLine.fileNames[0]) {
            // serve out generated ts file from memory, so we can avoid writing a temporary
            // file that causes conflicts on Windows
            text = tsText;
        } else {
            if (!ts.sys.fileExists(fileName)) {
                return undefined;
            } else {
                text = ts.sys.readFile(fileName)!;
            }
        }
        return {
            getText: (start: number, end: number) => {
                if (start === 0 && end === text.length) {
                    // optimization
                    return text;
                } else {
                    return text.slice(start, end);
                }
            },
            getLength: () => text.length,
            getChangeRange: (
                oldSnapshot: ts.IScriptSnapshot
            ): ts.TextChangeRange | undefined => {
                const oldText = oldSnapshot.getText(0, oldSnapshot.getLength());

                // Find the offset of the first char that differs between oldText and text
                let firstDiff = 0;
                while (
                    firstDiff < oldText.length &&
                    firstDiff < text.length &&
                    text[firstDiff] === oldText[firstDiff]
                ) {
                    firstDiff++;
                }

                // Find the offset of the last char that differs between oldText and text
                let oldIndex = oldText.length;
                let newIndex = text.length;
                while (
                    oldIndex > firstDiff &&
                    newIndex > firstDiff &&
                    oldText[oldIndex - 1] === text[newIndex - 1]
                ) {
                    oldIndex--;
                    newIndex--;
                }

                return {
                    span: {
                        start: firstDiff,
                        length: oldIndex - firstDiff,
                    },
                    newLength: newIndex - firstDiff,
                };
            },
            dispose: (): void => {
                text = "";
            },
        };
    },
    getCurrentDirectory: ts.sys.getCurrentDirectory,
    getDefaultLibFileName: ts.getDefaultLibFilePath,
};

const languageService = ts.createLanguageService(languageServiceHost);

function compile(tsPath, shims) {
    parsedCommandLine.fileNames = [tsPath, ...shims];
    const program = languageService.getProgram()!;
    const tsHost = ts.createCompilerHost(parsedCommandLine.options);
    const createdFiles = {};
    tsHost.writeFile = (fileName, contents) => (createdFiles[fileName] = contents);
    program.emit(undefined /* all files */, tsHost.writeFile);
    return createdFiles[parsedCommandLine.fileNames[0].replace(".ts", ".d.ts")];
}

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

async function writeDts(tsPath, dtsPath, shims) {
    const dtsSource = compile(tsPath, shims);
    await writeFile(dtsPath, dtsSource);
}

function writeTs(svelteSource, sveltePath) {
    let tsSource = svelte2tsx(svelteSource, {
        filename: sveltePath,
        strictMode: true,
        isTsFile: true,
    });
    let codeLines = tsSource.code.split("\n");
    // replace the "///<reference types="svelte" />" with a line
    // turning off checking, as we'll use svelte-check for that
    codeLines[0] = "// @ts-nocheck";
    // write to our global
    tsText = codeLines.join("\n");
}

async function writeJs(source, inputFilename, outputPath) {
    const preprocessOptions = preprocess({});
    preprocessOptions.filename = inputFilename;

    try {
        const processed = await svelte.preprocess(source, preprocessOptions);
        const result = svelte.compile(processed.toString!(), {
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
    const [sveltePath, mjsPath, dtsPath, ...shims] = args;
    const svelteSource = await readFile(sveltePath);

    // mock filename
    const tempTsPath = sveltePath + ".ts";

    await writeTs(svelteSource, sveltePath);
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
