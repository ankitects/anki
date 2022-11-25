// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { buildSync } from "esbuild";
import { argv } from "process";

const [_node, _script, entrypoint, js_out] = argv;

// support Qt 5.14
const target = ["es6", "chrome77"];

buildSync({
    bundle: false,
    entryPoints: [entrypoint],
    outfile: js_out,
    minify: true,
    preserveSymlinks: true,
    target,
});
