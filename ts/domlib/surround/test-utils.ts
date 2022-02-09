// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { ElementMatcher } from "./matcher";
import { MatchType } from "./matcher";

export const matchTagName =
    (tagName: string): ElementMatcher =>
    (element: Element) => {
        return { type: element.matches(tagName) ? MatchType.MATCH : MatchType.NONE };
    };

export const easyBold = {
    surroundElement: document.createElement("b"),
    matcher: matchTagName("b"),
};

export const easyUnderline = {
    surroundElement: document.createElement("u"),
    matcher: matchTagName("u"),
};

const parser = new DOMParser();

export function p(html: string): HTMLBodyElement {
    const parsed = parser.parseFromString(html, "text/html");
    return parsed.body as HTMLBodyElement;
}
