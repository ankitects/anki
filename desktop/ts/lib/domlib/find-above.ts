// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { nodeIsElement } from "@tslib/dom";

export type Matcher = (element: Element) => boolean;

function findParent(current: Node, base: Element): Element | null {
    if (current === base) {
        return null;
    }

    return current.parentElement;
}

/**
 * Similar to element.closest(), but allows you to pass in a predicate
 * function, instead of a selector
 *
 * @remarks
 * Unlike element.closest, this will not match against `node`, but will start
 * at `node.parentElement`.
 */
export function findClosest(
    node: Node,
    base: Element,
    matcher: Matcher,
): Element | null {
    if (nodeIsElement(node) && matcher(node)) {
        return node;
    }

    let current = findParent(node, base);

    while (current) {
        if (matcher(current)) {
            return current;
        }

        current = findParent(current, base);
    }

    return null;
}

/**
 * Similar to `findClosest`, but will go as far as possible.
 */
export function findFarthest(
    node: Node,
    base: Element,
    matcher: Matcher,
): Element | null {
    let farthest: Element | null = null;
    let current: Node | null = node;

    while (current) {
        const next = findClosest(current, base, matcher);

        if (next) {
            farthest = next;
            current = findParent(next, base);
        } else {
            break;
        }
    }

    return farthest;
}
