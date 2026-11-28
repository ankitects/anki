// Copyright: Anki Software, LLC and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/**
 * Must be imported before any mathjax/es5 module, as MathJax reads its
 * config when those modules are evaluated.
 *
 * All the components we use are bundled, so dynamic loading is never needed.
 * Without a custom `require`, MathJax's loader would inject a <script> tag for
 * unknown packages (e.g. `\require{foo}`), which Trusted Types blocks.
 */
(globalThis as any).MathJax = {
    loader: {
        require: (path: string): Promise<never> =>
            Promise.reject(new Error(`MathJax: dynamic loading is disabled (${path})`)),
    },
};
