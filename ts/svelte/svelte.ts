// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
// languageServerHost taken from MIT sources - see below.

const fs = require("fs");
const worker = require("@bazel/worker");
const svelte2tsx = require("svelte2tsx");
const preprocess = require("svelte-preprocess");
import { basename } from "path";
import * as ts from "typescript";
import * as svelte from "svelte/compiler.js";

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

// We avoid hitting the filesystem for ts/d.ts files after initial startup - the
// .ts file we generate can be injected directly into our cache, and Bazel
// should restart us if the Svelte or TS typings change.

interface FileContent {
    text: string;
    version: number;
}
const fileContent: Map<string, FileContent> = new Map();

function getFileContent(path: string): FileContent {
    let content = fileContent.get(path);
    if (!content) {
        content = {
            text: ts.sys.readFile(path)!,
            version: 0,
        };
        fileContent.set(path, content);
    }
    return content;
}

function updateFileContent(path: string, text: string): void {
    let content = fileContent.get(path);
    if (content) {
        content.text = text;
        content.version += 1;
    } else {
        content = {
            text,
            version: 0,
        };
        fileContent.set(path, content);
    }
}

// based on https://github.com/Asana/bazeltsc/blob/7dfa0ba2bd5eb9ee556e146df35cf793fad2d2c3/src/bazeltsc.ts (MIT)
const languageServiceHost: ts.LanguageServiceHost = {
    getCompilationSettings: (): ts.CompilerOptions => parsedCommandLine.options,
    getScriptFileNames: (): string[] => parsedCommandLine.fileNames,
    getScriptVersion: (path: string): string => {
        return getFileContent(path).version.toString();
    },
    getScriptSnapshot: (path: string): ts.IScriptSnapshot | undefined => {
        // if (!ts.sys.fileExists(fileName)) {
        const text = getFileContent(path).text;
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
                return undefined;
            },
        };
    },
    getCurrentDirectory: ts.sys.getCurrentDirectory,
    getDefaultLibFileName: ts.getDefaultLibFilePath,
};

const languageService = ts.createLanguageService(languageServiceHost);

function compile(tsPath: string, tsLibs: string[]) {
    parsedCommandLine.fileNames = [tsPath, ...tsLibs];
    const program = languageService.getProgram()!;
    const tsHost = ts.createCompilerHost(parsedCommandLine.options);
    const createdFiles = {};
    tsHost.writeFile = (fileName, contents) => (createdFiles[fileName] = contents);
    program.emit(undefined /* all files */, tsHost.writeFile);
    return createdFiles[parsedCommandLine.fileNames[0].replace(".tsx", ".d.ts")];
}

function writeFile(file, data): Promise<void> {
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

async function writeDts(tsPath, dtsPath, tsLibs) {
    const dtsSource = compile(tsPath, tsLibs);
    await writeFile(dtsPath, dtsSource);
}

function writeTs(svelteSource, sveltePath, tsPath): void {
    let tsSource = svelte2tsx(svelteSource, {
        filename: sveltePath,
        strictMode: true,
        isTsFile: true,
    });
    let codeLines = tsSource.code.split("\n");
    // replace the "///<reference types="svelte" />" with a line
    // turning off checking, as we'll use svelte-check for that
    codeLines[0] = "// @ts-nocheck";
    updateFileContent(tsPath, codeLines.join("\n"));
}

async function writeJs(
    source: string,
    inputFilename: string,
    outputJsPath: string,
    outputCssPath: string,
    binDir: string,
    genDir: string
): Promise<void> {
    const preprocessOptions = preprocess({
        scss: {
            includePaths: [
                binDir,
                genDir,
                // a nasty hack to ensure ts/sass/... resolves correctly
                // when invoked from an external workspace
                binDir + "/external/ankidesktop",
                genDir + "/external/ankidesktop",
                binDir + "/../../../external/ankidesktop",
            ],
        },
    });
    preprocessOptions.filename = inputFilename;

    try {
        const processed = await svelte.preprocess(source, preprocessOptions);
        const result = svelte.compile(processed.toString!(), {
            format: "esm",
            css: false,
            generate: "dom",
            filename: outputJsPath,
        });
        // warnings are an error
        if (result.warnings.length > 0) {
            console.log(`warnings during compile: ${result.warnings}`);
        }
        // write out the css file
        const outputCss = result.css.code ?? "";
        await writeFile(outputCssPath, outputCss);
        // if it was non-empty, prepend a reference to it in the js file, so that
        // it's included in the bundled .css when bundling
        const outputSource =
            (outputCss ? `import "./${basename(outputCssPath)}";` : "") +
            result.js.code;
        await writeFile(outputJsPath, outputSource);
    } catch (err) {
        console.log(`compile failed: ${err}`);
        return;
    }
}

async function compileSvelte(args) {
    const [sveltePath, mjsPath, dtsPath, cssPath, binDir, genDir, ...tsLibs] = args;
    const svelteSource = (await readFile(sveltePath)) as string;

    const mockTsPath = sveltePath + ".tsx";
    writeTs(svelteSource, sveltePath, mockTsPath);
    await writeDts(mockTsPath, dtsPath, tsLibs);
    await writeJs(svelteSource, sveltePath, mjsPath, cssPath, binDir, genDir);

    return true;
}

function main() {
    if (worker.runAsWorker(process.argv)) {
        console.log = worker.log;
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
    main();
    process.exitCode = 0;
}
