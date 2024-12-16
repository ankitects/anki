// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
import svg from "@poppanator/sveltekit-svg";
import { sveltekit } from "@sveltejs/kit/vite";
import { realpathSync } from "fs";
import { defineConfig as defineViteConfig, mergeConfig } from "vite";
import { defineConfig as defineVitestConfig } from "vitest/config";

const configure = (proxy: any, _options: any) => {
    proxy.on("error", (err: any) => {
        console.log("proxy error", err);
    });
    proxy.on("proxyReq", (proxyReq: any, req: any) => {
        console.log("Sending Request to the Target:", req.method, req.url);
    });
    proxy.on("proxyRes", (proxyRes: any, req: any) => {
        console.log("Received Response from the Target:", proxyRes.statusCode, req.url);
    });
};

const viteConfig = defineViteConfig({
    plugins: [sveltekit(), svg({})],
    build: {
        reportCompressedSize: false,
        // defaults use chrome87, but we need 77 for qt 5.14
        target: ["es2020", "edge88", "firefox78", "chrome77", "safari14"],
    },
    server: {
        host: "127.0.0.1",
        fs: {
            // Allow serving files project root and out dir
            allow: [
                // realpathSync(".."),
                // "/home/dae/Local/build/anki/node_modules",
                realpathSync("../out"),
                // realpathSync("../out/node_modules"),
            ],
        },
        proxy: {
            "/_anki": {
                target: "http://127.0.0.1:40000",
                changeOrigin: true,
                autoRewrite: true,
                configure,
            },
        },
    },
});

const vitestConfig = defineVitestConfig({
    test: {
        include: ["**/*.{test,spec}.{js,ts}"],
        cache: {
            // prevent vitest from creating ts/node_modules/.vitest
            dir: "../node_modules/.vitest",
        },
    },
});

export default mergeConfig(viteConfig, vitestConfig);
