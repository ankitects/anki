// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { ElementMatcher } from "./matcher";
import { MatchResult } from "./matcher";

export const matchTagName =
    (tagName: string): ElementMatcher =>
    (element: Element) => {
        return element.matches(tagName) ? MatchResult.MATCH : MatchResult.NO_MATCH;
    };

export const easyBold = {
    surroundElement: document.createElement("b"),
    matcher: matchTagName("b"),
    clearer: (): boolean => false,
};

export const easyUnderline = {
    surroundElement: document.createElement("u"),
    matcher: matchTagName("u"),
    clearer: (): boolean => false,
};

const parser = new DOMParser();

export function p(html: string): HTMLBodyElement {
    const parsed = parser.parseFromString(html, "text/html");
    return parsed.body as HTMLBodyElement;
}
