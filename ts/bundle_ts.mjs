// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { build } from "esbuild";
import { argv } from "process";

const [_node, _script, entrypoint, bundle_js] = argv;

// support Qt 5.14
const target = ["es6", "chrome77"];

build({
    bundle: true,
    entryPoints: [entrypoint],
    outfile: bundle_js,
    minify: true,
    preserveSymlinks: true,
    target,
}).catch(() => process.exit(1));
