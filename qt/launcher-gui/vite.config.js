import svg from "@poppanator/sveltekit-svg";
import { sveltekit } from "@sveltejs/kit/vite";
import { realpathSync } from "fs";
import { defineConfig } from "vite";

const host = process.env.TAURI_DEV_HOST;

// https://vite.dev/config/
export default defineConfig(async () => ({
    plugins: [sveltekit(), svg({})],

    // Vite options tailored for Tauri development and only applied in `tauri dev` or `tauri build`
    //
    // 1. prevent Vite from obscuring rust errors
    clearScreen: false,
    // 2. tauri expects a fixed port, fail if that port is not available
    server: {
        port: 1420,
        strictPort: true,
        host: host || false,
        hmr: host
            ? {
                protocol: "ws",
                host,
                port: 1421,
            }
            : undefined,
        watch: {
            // 3. tell Vite to ignore watching `src-tauri`
            ignored: ["**/src-tauri/**"],
        },
        fs: {
            allow: [
                realpathSync("../../out"),
                realpathSync("../../ts"),
            ],
        },
    },
}));
