import resolve from "@rollup/plugin-node-resolve";
import commonjs from "@rollup/plugin-commonjs";
import json from "@rollup/plugin-json";
import ignore from "rollup-plugin-ignore";
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
        json(),
        commonjs(),
        // imported by sanitize-html->postcss
        ignore(["path", "url"]),
        production && terser(),
    ],
    onwarn: function (warning, warn) {
        if (warning.code === "CIRCULAR_DEPENDENCY") return;
        throw warning;
    },
};
