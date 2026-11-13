// Copyright: Anki Software, LLC and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { TrustedTypePolicyFactory } from "trusted-types";

import { createDummyDoc } from "@tslib/parsing";

declare global {
    let trustedTypes: TrustedTypePolicyFactory | undefined;
}

if (typeof trustedTypes === "undefined") {
    window.trustedTypes = {
        createPolicy: (_name, rules) => rules,
    } as TrustedTypePolicyFactory;
}

trustedTypes!.createPolicy("default", {
    createHTML(input: string) {
        console.trace("Raw string passed to HTML sink:", input);
        return input;
    },
    createScript(input: string) {
        console.trace("Raw string passed to JavaScript sink:", input);
        return input;
    },
    createScriptURL(input: string) {
        console.trace("Raw string passed to JavaScript URL sink:", input);
        return input;
    },
});

const trustedPolicy = trustedTypes!.createPolicy("anki-trusted", {
    createHTML(value: string) {
        return value;
    },
    createScript(_input: string) {
        throw TypeError("createScript() is not supported for the anki-trusted policy");
    },
    createScriptURL(_input: string) {
        throw TypeError("createScriptURL() is not supported for the anki-trusted policy");
    },
});

export function trustedHTML(html: string) {
    return trustedPolicy!.createHTML(html);
}

const parser = new DOMParser();

function removeTag(element: HTMLElement, tagName: string): void {
    for (const elem of element.getElementsByTagName(tagName)) {
        elem.remove();
    }
}

const prohibitedTags = ["script", "link", "base"];

/**
 * The use cases for using those tags in the field html are slim to none.
 * We want to make it easier to possibly display cards in an iframe in the future.
 */
function removeProhibitedTags(html: string): string {
    const doc = parser.parseFromString(trustedHTML(createDummyDoc(html)) as unknown as string, "text/html");
    const body = doc.body;

    for (const tag of prohibitedTags) {
        removeTag(body, tag);
    }

    return doc.body.innerHTML;
}

const userContentPolicy = trustedTypes!.createPolicy("anki-user-content", {
    createHTML(value: string) {
        return removeProhibitedTags(value);
    },
    createScript(_input: string) {
        throw TypeError("createScript() is not supported for the anki-user-content policy");
    },
    createScriptURL(_input: string) {
        throw TypeError("createScript() is not supported for the anki-user-content policy");
    },
});

export function untrustedHTML(html: string) {
    return userContentPolicy!.createHTML(html);
}
