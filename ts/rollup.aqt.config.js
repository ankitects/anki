import resolve from "@rollup/plugin-node-resolve";
import commonjs from "@rollup/plugin-commonjs";
import { terser } from "rollup-plugin-terser";

import process from "process";
const production = process.env["COMPILATION_MODE"] === "opt";

export default {
    output: {
        name: "globalThis",
        extend: true,
        format: "iife",
    },
    plugins: [resolve({ browser: true }), commonjs(), production && terser()],
};
