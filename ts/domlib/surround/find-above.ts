// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { nodeIsElement } from "../../lib/dom";
import type { FoundMatch, ElementMatcher } from "./matcher";

export function findClosest(
    node: Node,
    base: Element,
    matcher: ElementMatcher,
): FoundMatch | null {
    let current: Node | Element = node;

    while (true) {
        if (nodeIsElement(current)) {
            const matchType = matcher(current);

            if (matchType) {
                return {
                    element: current,
                    matchType,
                };
            }
        }

        if (current.isSameNode(base) || !current.parentElement) {
            return null;
        }

        current = current.parentElement;
    }
}

export function findFarthest(
    node: Node,
    base: Element,
    matcher: ElementMatcher,
): FoundMatch | null {
    let found: FoundMatch | null = null;
    let current: Node | Element = node;

    while (true) {
        if (nodeIsElement(current) && matcher(current)) {
            const matchType = matcher(current);

            if (matchType) {
                return {
                    element: current,
                    matchType,
                };
            }
        }

        if (current.isSameNode(base) || !current.parentElement) {
            return found;
        }

        current = current.parentElement;
    }
}
