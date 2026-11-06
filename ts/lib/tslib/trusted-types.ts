// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { TrustedTypePolicyFactory } from "trusted-types";

declare global {
    let trustedTypes: TrustedTypePolicyFactory | undefined;
}

if (typeof trustedTypes === "undefined") {
    window.trustedTypes = {
        createPolicy: (_name, rules) => rules,
    } as TrustedTypePolicyFactory;
}

trustedTypes!.createPolicy("default", {
    createHTML(_input: string) {
        throw TypeError("Please refactor this code to use the Trusted Types API");
    },
    createScript(_input: string) {
        throw TypeError("Please refactor this code to use the Trusted Types API");
    },
    createScriptURL(_input: string) {
        throw TypeError("Please refactor this code to use the Trusted Types API");
    },
});
