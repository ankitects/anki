// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { nodeIsElement } from "../../lib/dom";
import type { FoundMatch, ElementMatcher } from "./matcher";

export function findClosest(
    node: Node,
    base: Element,
    matcher: ElementMatcher,
): FoundMatch | null {
    let current: Node | Element | null = node;

    while (current) {
        if (nodeIsElement(current)) {
            const matchType = matcher(current);
            if (matchType) {
                return {
                    element: current,
                    matchType,
                };
            }
        }

        current =
            current.isSameNode(base) || !current.parentElement
                ? null
                : current.parentElement;
    }

    return current;
}

export function findFarthest(
    node: Node,
    base: Element,
    matcher: ElementMatcher,
): FoundMatch | null {
    let found: FoundMatch | null = null;
    let current: Node | Element | null = node;

    while (current) {
        if (nodeIsElement(current)) {
            const matchType = matcher(current);
            if (matchType) {
                found = {
                    element: current,
                    matchType,
                };
            }
        }

        current =
            current.isSameNode(base) || !current.parentElement
                ? null
                : current.parentElement;
    }

    return found;
}
