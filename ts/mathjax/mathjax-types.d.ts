// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/* eslint
@typescript-eslint/no-explicit-any: "off",
 */

export {};

declare global {
    interface Window {
        // Mathjax does not provide a full type
        MathJax: { [name: string]: any };
    }
}
