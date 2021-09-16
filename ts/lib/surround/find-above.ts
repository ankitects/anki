// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { ElementMatcher } from "./matcher";
import { MatchResult } from "./matcher";
import { nodeIsElement } from "../dom";

type FindAboveResult = [found: Element, isKeep: boolean] | null;

export function findClosest(
    node: Node,
    base: Element,
    matcher: ElementMatcher,
): FindAboveResult {
    let current: Node | Element = node;

    while (true) {
        if (nodeIsElement(current)) {
            const matchResult = matcher(current);

            if (matchResult) {
                return [current, matchResult === MatchResult.KEEP];
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
): FindAboveResult {
    let found: FindAboveResult = null;
    let current: Node | Element = node;

    while (true) {
        if (nodeIsElement(current) && matcher(current)) {
            const matchResult = matcher(current);

            if (matchResult) {
                found = [current, matchResult === MatchResult.KEEP];
            }
        }

        if (current.isSameNode(base) || !current.parentElement) {
            return found;
        }

        current = current.parentElement;
    }
}
