// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/* eslint
@typescript-eslint/no-explicit-any: "off",
 */

/// This can be extended to allow require() calls at runtime, for libraries
/// that are not included at bundling time.
export const runtimeLibraries = {};

// Export require() as a global.
(window as any).require = function (name: string): unknown {
    const lib = runtimeLibraries[name];
    if (lib === undefined) {
        throw new Error(`Cannot require(${name}) at runtime.`);
    }
    return lib;
};
