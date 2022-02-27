// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
// languageServerHost taken from MIT sources - see below.

import * as fs from "fs";
import * as worker from "@bazel/worker";
import { svelte2tsx } from "svelte2tsx";
import preprocess from "svelte-preprocess";
import { basename } from "path";
import * as ts from "typescript";
import * as svelte from "svelte/compiler";

const parsedCommandLine: ts.ParsedCommandLine = {
    fileNames: [],
    errors: [],
    options: {
        jsx: ts.JsxEmit.Preserve,
        declaration: true,
        emitDeclarationOnly: true,
        // noEmitOnError: true,
        paths: {
            "*": ["*", "external/npm/node_modules/*"],
        },
    },
};

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

function updateFileContent(input: InputFile): void {
    let content = fileContent.get(input.path);
    if (content && content.text !== input.data) {
        content.text = input.data;
        content.version += 1;
    } else {
        content = {
            text: input.data,
            version: 0,
        };
        fileContent.set(input.path, content);
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
                _oldSnapshot: ts.IScriptSnapshot,
            ): ts.TextChangeRange | undefined => {
                return undefined;
            },
        };
    },
    getCurrentDirectory: ts.sys.getCurrentDirectory,
    getDefaultLibFileName: ts.getDefaultLibFilePath,
};

const languageService = ts.createLanguageService(languageServiceHost);

async function emitTypings(svelte: SvelteTsxFile[], deps: InputFile[]): Promise<void> {
    const allFiles = [...svelte, ...deps];
    allFiles.forEach(updateFileContent);
    parsedCommandLine.fileNames = allFiles.map((i) => i.path);
    const program = languageService.getProgram()!;
    const tsHost = ts.createCompilerHost(parsedCommandLine.options);
    const createdFiles = {};
    const cwd = ts.sys.getCurrentDirectory().replace(/\\/g, "/");
    tsHost.writeFile = (fileName: string, contents: string): void => {
        // tsc makes some paths absolute for some reason
        if (fileName.startsWith(cwd)) {
            fileName = fileName.substring(cwd.length + 1);
        }
        createdFiles[fileName] = contents;
    };
    const result = program.emit(undefined /* all files */, tsHost.writeFile);
    // for (const diag of result.diagnostics) {
    //     console.log(diag.messageText);
    // }

    for (const file of svelte) {
        if (!(file.virtualDtsPath in createdFiles)) {
            /**
             * This can happen if you do a case-only rename
             * e.g. NoteTypeButtons.svelte -> NotetypeButtons.svelte
             */
            console.log(
                "file not among created files: ",
                file.virtualDtsPath,
                Object.keys(createdFiles),
            );
        } else {
            await writeFile(file.realDtsPath, createdFiles[file.virtualDtsPath]);
        }
    }
}

async function writeFile(file: string, data: string): Promise<void> {
    await fs.promises.writeFile(file, data);
}

function readFile(file: string): Promise<string> {
    return fs.promises.readFile(file, "utf-8");
}

async function compileSingleSvelte(
    input: SvelteInput,
    binDir: string,
    genDir: string,
): Promise<void> {
    const preprocessOptions = preprocess({
        scss: {
            includePaths: [
                binDir,
                genDir,
                // a nasty hack to ensure sass/... resolves correctly
                // when invoked from an external workspace
                `${binDir}/external/ankidesktop`,
                `${genDir}/external/ankidesktop`,
                `${binDir}/external/ankidesktop/sass`,
                `${binDir}/../../../external/ankidesktop`,
            ],
        },
    });

    try {
        const processed = await svelte.preprocess(input.data, preprocessOptions, {
            filename: input.path,
        });
        const result = svelte.compile(processed.toString!(), {
            format: "esm",
            css: false,
            generate: "dom",
            filename: input.mjsPath,
        });
        // warnings are an error
        if (result.warnings.length > 0) {
            console.log(`warnings during compile: ${result.warnings}`);
        }
        // write out the css file
        const outputCss = result.css.code ?? "";
        await writeFile(input.cssPath, outputCss);
        // if it was non-empty, prepend a reference to it in the js file, so that
        // it's included in the bundled .css when bundling
        const outputSource =
            (outputCss ? `import "./${basename(input.cssPath)}";` : "") +
            result.js.code;
        await writeFile(input.mjsPath, outputSource);
    } catch (err) {
        console.log(`compile failed: ${err}`);
        return;
    }
}

interface Args {
    binDir: string;
    genDir: string;
    svelteFiles: SvelteInput[];
    dependencies: InputFile[];
}

interface InputFile {
    path: string;
    data: string;
}

interface SvelteInput extends InputFile {
    dtsPath: string;
    cssPath: string;
    mjsPath: string;
}

async function extractArgsAndData(args: string[]): Promise<Args> {
    const [binDir, genDir, ...rest] = args;
    const [svelteFiles, dependencies] = await extractSvelteAndDeps(rest);
    return {
        binDir,
        genDir,
        svelteFiles,
        dependencies,
    };
}

async function extractSvelteAndDeps(
    files: string[],
): Promise<[SvelteInput[], InputFile[]]> {
    const svelte: SvelteInput[] = [];
    const deps: InputFile[] = [];
    files.reverse();
    while (files.length) {
        const file = files.pop()!;
        const data = (await readFile(file)) as string;
        if (file.endsWith(".svelte")) {
            svelte.push({
                path: file,
                data,
                dtsPath: files.pop()!,
                cssPath: files.pop()!,
                mjsPath: files.pop()!,
            });
        } else {
            deps.push({ path: remapBinToSrcDir(file), data });
        }
    }
    return [svelte, deps];
}

/// Our generated .tsx files sit in the bin dir,  but .ts files
/// may be coming from the source folder, which breaks ./foo imports.
/// Adjust the path to make it appear they're all in the same folder.
function remapBinToSrcDir(file: string): string {
    return file.replace(new RegExp("bazel-out/[-_a-z]+/bin/"), "");
}

/// Generate Svelte .mjs/.css files.
async function compileSvelte(
    svelte: SvelteInput[],
    binDir: string,
    genDir: string,
): Promise<void> {
    for (const file of svelte) {
        await compileSingleSvelte(file, binDir, genDir);
    }
}

interface SvelteTsxFile extends InputFile {
    // relative to src folder
    virtualDtsPath: string;
    // must go to bazel-out
    realDtsPath: string;
}

function generateTsxFiles(svelteFiles: SvelteInput[]): SvelteTsxFile[] {
    return svelteFiles.map((file) => {
        const data = svelte2tsx(file.data, {
            filename: file.path,
            isTsFile: true,
            mode: "dts",
        }).code;
        const path = file.path.replace(".svelte", ".svelte.tsx");
        return {
            path,
            data,
            virtualDtsPath: path.replace(".tsx", ".d.ts"),
            realDtsPath: file.dtsPath,
        };
    });
}

async function compileSvelteAndGenerateTypings(argsList: string[]): Promise<boolean> {
    const args = await extractArgsAndData(argsList);

    // mjs/css
    await compileSvelte(args.svelteFiles, args.binDir, args.genDir);

    // d.ts
    const tsxFiles = generateTsxFiles(args.svelteFiles);
    await emitTypings(tsxFiles, args.dependencies);

    return true;
}

function main() {
    if (worker.runAsWorker(process.argv)) {
        console.log = worker.log;
        worker.log("Svelte running as a Bazel worker");
        worker.runWorkerLoop(compileSvelteAndGenerateTypings);
    } else {
        const paramFile = process.argv[2].replace(/^@/, "");
        const commandLineArgs = fs.readFileSync(paramFile, "utf-8").trim().split("\n");
        console.log("Svelte running as a standalone process");
        compileSvelteAndGenerateTypings(commandLineArgs);
    }
}

if (require.main === module) {
    main();
    process.exitCode = 0;
}
