import resolve from "@rollup/plugin-node-resolve";
import commonjs from "@rollup/plugin-commonjs";
import { terser } from "rollup-plugin-terser";

import process from "process";
const production = process.env["COMPILATION_MODE"] === "opt";

export default {
    external: ["protobufjs/light"],
    output: {
        globals: {
            "protobufjs/light": "protobuf",
        },
        name: "anki",
    },
    plugins: [
        resolve({
            browser: true,
            dedupe: ["svelte", "protobufjs"],
        }),
        commonjs(),
        production && terser(),
    ],
    onwarn: function (warning, warn) {
        if (warning.code === "CIRCULAR_DEPENDENCY") return;
        throw warning;
    },
};
