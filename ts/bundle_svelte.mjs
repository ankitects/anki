// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { build } from "esbuild";
import { sassPlugin } from "esbuild-sass-plugin";
import sveltePlugin from "esbuild-svelte";
import { readFileSync, writeFileSync } from "fs";
import { basename } from "path";
import { argv, env } from "process";
import sveltePreprocess from "svelte-preprocess";
import { typescript } from "svelte-preprocess-esbuild";

const [_tsx, _script, entrypoint, bundle_js, bundle_css, page_html] = argv;

if (page_html != null) {
    const template = readFileSync("ts/page.html", { encoding: "utf8" });
    writeFileSync(page_html, template.replace(/{PAGE}/g, basename(page_html, ".html")));
}

// support Qt 5.14
const target = ["es2020", "chrome77"];
const inlineCss = bundle_css == null;
const sourcemap = env.SOURCEMAP && true;
let sveltePlugins;

if (!sourcemap) {
    sveltePlugins = [
        // use esbuild for faster typescript transpilation
        typescript({
            target,
            define: {
                "process.browser": "true",
            },
            tsconfig: "ts/tsconfig_legacy.json",
        }),
        sveltePreprocess({ typescript: false }),
    ];
} else {
    sveltePlugins = [
        // use tsc for more accurate sourcemaps
        sveltePreprocess({ typescript: true, sourceMap: true }),
    ];
}

build({
    bundle: true,
    entryPoints: [entrypoint],
    globalName: "anki",
    outfile: bundle_js,
    minify: env.RELEASE && true,
    loader: { ".svg": "text" },
    preserveSymlinks: true,
    sourcemap: sourcemap ? "inline" : false,
    plugins: [
        sassPlugin({ loadPaths: ["node_modules"] }),
        sveltePlugin({
            compilerOptions: { css: inlineCss ? "injected" : "external" },
            preprocess: sveltePlugins,
            // let us focus on errors; we can see the warnings with svelte-check
            filterWarnings: (_warning) => false,
        }),
    ],
    target,
    // logLevel: "info",
}).catch(() => process.exit(1));
