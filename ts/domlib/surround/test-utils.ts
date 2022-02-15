// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { ElementMatcher } from "./match-type";
import { MatchType, userFormatToFormat } from "./match-type";

export const matchTagName =
    (tagName: string): ElementMatcher =>
    (element: Element) => {
        return { type: element.matches(tagName) ? MatchType.REMOVE : MatchType.NONE };
    };

export const easyBold = userFormatToFormat({
    surroundElement: document.createElement("b"),
    matcher: matchTagName("b"),
});

export const easyItalic = userFormatToFormat({
    surroundElement: document.createElement("i"),
    matcher: matchTagName("i"),
});

export const easyUnderline = userFormatToFormat({
    surroundElement: document.createElement("u"),
    matcher: matchTagName("u"),
});

const parser = new DOMParser();

export function p(html: string): HTMLBodyElement {
    const parsed = parser.parseFromString(html, "text/html");
    return parsed.body as HTMLBodyElement;
}

export function t(data: string): Text {
    return document.createTextNode(data);
}

function element(tagName: string): (...childNodes: Node[]) => HTMLElement {
    return function (...childNodes: Node[]): HTMLElement {
        const element = document.createElement(tagName);
        element.append(...childNodes);
        return element;
    };
}

export const b = element("b");
export const i = element("i");
export const u = element("u");
export const span = element("span");
export const div = element("div");
