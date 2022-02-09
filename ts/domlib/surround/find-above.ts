// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { ElementMatcher, FoundMatch } from "./matcher";
import { applyMatcher } from "./matcher";

function tryMatch(current: Node, matcher: ElementMatcher): FoundMatch | null {
    const match = applyMatcher(matcher, current);

    if (match.type) {
        return {
            element: current as HTMLElement | SVGElement,
            match,
        };
    }

    return null;
}

function findParent(current: Node, base: Element): Element | null {
    if (current === base || !current.parentElement) {
        return null;
    } else {
        return current.parentElement;
    }
}

export function findClosest(
    node: Node,
    base: Element,
    matcher: ElementMatcher,
): FoundMatch | null {
    let current: Node | Element | null = node;

    while (current) {
        const match = tryMatch(current, matcher);

        if (match) {
            return match;
        }

        current = findParent(current, base);
    }

    return null;
}

export function findFarthest(
    node: Node,
    base: Element,
    matcher: ElementMatcher,
): FoundMatch | null {
    let farthest: FoundMatch | null = null;
    let current: Node | Element | null = node;

    while (current) {
        const next = findClosest(current, base, matcher);

        if (next) {
            farthest = next;
            current = findParent(next.element, base);
        } else {
            break;
        }
    }

    return farthest;
}
