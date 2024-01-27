import adapter from "@sveltejs/adapter-static";
import { vitePreprocess } from "@sveltejs/vite-plugin-svelte";
import { realpathSync } from "fs";
import { resolve } from "path";

/** @type {import('@sveltejs/kit').Config} */
const config = {
    preprocess: vitePreprocess(),

    kit: {
        adapter: adapter(
            { pages: realpathSync("../out/ts-out"), fallback: "index.html", precompress: true },
        ),
        alias: {
            "@tslib": resolve("./lib"),
            "@generated": realpathSync("../out/ts/generated"),
        },
        files: {
            lib: "./",
            routes: "./routes",
        },
        outDir: realpathSync("../out/svelte-kit"),
        output: { preloadStrategy: "preload-mjs" },
        prerender: {
            crawl: false,
            entries: [],
        },
        version: { pollInterval: 1000 * 60 * 15 },
        paths: {},
    },
};

export default config;
