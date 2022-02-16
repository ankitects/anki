// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

export type Matcher = (element: Element) => boolean

function tryMatch(current: Element, matcher: Matcher): Element | null {
    if (!matcher(current)) {
        return null;
    }

    return current;
}

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
    let current = findParent(node, base);

    while (current) {
        const match = tryMatch(current, matcher);

        if (match) {
            return match;
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
    let current = findParent(node, base);

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
