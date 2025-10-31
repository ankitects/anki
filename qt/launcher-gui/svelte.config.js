// Tauri doesn't have a Node.js server to do proper SSR
// so we use adapter-static with a fallback to index.html to put the site in SPA mode
// See: https://svelte.dev/docs/kit/single-page-apps
// See: https://v2.tauri.app/start/frontend/sveltekit/ for more info
import adapter from "@sveltejs/adapter-static";
import { vitePreprocess } from "@sveltejs/vite-plugin-svelte";
import { dirname, join } from "path";
import { sveltePreprocess } from "svelte-preprocess";
import { fileURLToPath } from "url";

// This prevents errors being shown when opening VSCode on the root of the
// project, instead of the ts folder.
const tsFolder = dirname(fileURLToPath(import.meta.url));

/** @type {import('@sveltejs/kit').Config} */
const config = {
    preprocess: [vitePreprocess(), sveltePreprocess()],
    kit: {
        adapter: adapter({
            fallback: "index.html",
        }),
        alias: {
            "@tslib": join(tsFolder, "../../ts/lib/tslib"),
            "@generated": join(tsFolder, "../../out/ts/lib/generated"),
        },
        files: {
            lib: join(tsFolder, "../../ts/lib"),
        },
    },
};

export default config;
