// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { expect, test } from "./fixtures";

// Files that MathJax loads on demand at runtime, and which therefore aren't
// pulled in by tex-chtml-full.js. If any of these is missing from
// MATHJAX_FILES in build/configure/src/web.rs, the corresponding feature
// silently breaks (eg a blank card after switching the renderer to SVG).
const RUNTIME_DEPENDENCIES = [
    // Math Settings > Math Renderer > SVG
    "output/svg.js",
    "output/svg/fonts/tex.js",
    // dependencies of a11y/semantic-enrich (used by the explorer/complexity)
    "a11y/sre.js",
    "input/mml.js",
    // speech rule engine locales
    "sre/mathmaps/base.json",
    "sre/mathmaps/ca.json",
    "sre/mathmaps/da.json",
    "sre/mathmaps/nb.json",
    "sre/mathmaps/nn.json",
    "sre/mathmaps/sv.json",
];

for (const path of RUNTIME_DEPENDENCIES) {
    test(`bundled MathJax includes ${path}`, async ({ request }) => {
        const response = await request.get(`/_anki/js/vendor/mathjax/${path}`);
        expect(response.status()).toBe(200);
    });
}
