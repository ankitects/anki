// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/// <reference types="./mathjax-types" />

const packages = ["noerrors", "mathtools"];

function packagesForLoading(packages: string[]): string[] {
    return packages.map((value: string): string => `[tex]/${value}`);
}

window.MathJax = {
    tex: {
        displayMath: [["\\[", "\\]"]],
        processEscapes: false,
        processEnvironments: false,
        processRefs: false,
        packages: {
            "[+]": packages,
            "[-]": ["textmacros"],
        },
    },
    loader: {
        load: packagesForLoading(packages),
        paths: {
            mathjax: "/_anki/js/vendor/mathjax",
        },
    },
    startup: {
        typeset: false,
    },
};
