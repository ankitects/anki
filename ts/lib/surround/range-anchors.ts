// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { ElementMatcher } from "./matcher";
import { MatchResult } from "./matcher";
import { nodeIsElement } from "../dom";
import { splitPartiallySelectedTextNodes } from "./text-node";

function textOrMatches(node: Node, matcher: ElementMatcher): boolean {
    return !nodeIsElement(node) || matcher(node as Element) === MatchResult.MATCH;
}

function findBelow(element: Element, matcher: ElementMatcher): Node | null {
    while (element.hasChildNodes()) {
        const node = element.childNodes[element.childNodes.length - 1];

        if (textOrMatches(node, matcher)) {
            return node;
        }

        element = node as Element;
    }

    return null;
}

function findAbove(element: Element, matcher: ElementMatcher): Node | null {
    if (element.parentNode) {
        const index = Array.prototype.indexOf.call(element.parentNode, element);

        if (index > 0) {
            const before = element.parentNode.childNodes[index - 1];

            if (textOrMatches(before, matcher)) {
                return before;
            }
        }
    }

    return null;
}

function findFittingNode(node: Node, matcher: ElementMatcher): Node {
    if (textOrMatches(node, matcher)) {
        return node;
    }

    return (
        findBelow(node as Element, matcher) ??
        findAbove(node as Element, matcher) ??
        (console.log("anki: findFittingNode returns invalid node"), node)
    );
}

function negate(matcher: ElementMatcher): ElementMatcher {
    return (element: Element) => {
        const matchResult = matcher(element);

        switch (matchResult) {
            case MatchResult.NO_MATCH:
                return MatchResult.MATCH;
            case MatchResult.MATCH:
                return MatchResult.NO_MATCH;
            default:
                return matchResult;
        }
    };
}

interface RangeAnchors {
    start: Node;
    end: Node;
}

export function getRangeAnchors(range: Range, matcher: ElementMatcher): RangeAnchors {
    const { start, end } = splitPartiallySelectedTextNodes(range);

    return {
        start:
            start ??
            findFittingNode(
                range.startContainer.childNodes[range.startOffset],
                negate(matcher),
            ),
        end:
            end ??
            findFittingNode(
                range.endContainer.childNodes[range.endOffset - 1],
                negate(matcher),
            ),
    };
}
