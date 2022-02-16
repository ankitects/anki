// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { nodeIsCommonElement } from "../../lib/dom";
import type { FormattingNode, MatchNode } from "./formatting-tree";

export enum MatchType {
    /**
     * An element unrelated to the surround format.
     * The value of NONE is 0, which allows for falsy tests.
     */
    NONE = 0,
    /** Element matches the predicate and may be removed */
    REMOVE,
    /**
     * Element matches the predicate, but may not be removed.
     *
     * @remarks
     * This typically means that the element has other properties which prevent
     * it from being removed. E.g. an element matches a bold predicate, because
     * it has inline styling of `font-weight: bold`, but it also has other
     * inline styling aplied additionally.
     */
    CLEAR,
}

interface MatchNone {
    type: MatchType.NONE;
}

interface MatchRemove {
    type: MatchType.REMOVE;
}

/**
 * Applied an element that matched with MatchType.CLEAR.
 *
 * @remarks
 * Should be idempotent.
 */
type ElementClearer = (element: HTMLElement | SVGElement) => boolean;

interface MatchClear {
    type: MatchType.CLEAR;
    clear: ElementClearer;
}

export type Match = MatchNone | MatchRemove | MatchClear;

/**
 * A function to determine how an element relates to a element predicate.
 *
 * @remarks
 * Should be pure.
 *
 * @example
 * A predicate could be "is bold", which could match `b` and `strong` tags.
 */
export type ElementMatcher = (element: HTMLElement | SVGElement) => Match;

export interface FoundMatch {
    match: MatchRemove | MatchClear;
    element: HTMLElement | SVGElement;
}

// export type RangeMerger = (before: ChildNodeRange, after: ChildNodeRange) => boolean;

// TODO REMOVE
/**
 * We want to avoid that users have to deal with the difference between Element
 * and {HTML,SVG}Element, which is probably not vital in practice
 */
function apply<T>(
    constant: T,
): (applied: (element: HTMLElement | SVGElement) => T, node: Node) => T {
    return function (applied: (element: HTMLElement | SVGElement) => T, node: Node): T {
        if (!nodeIsCommonElement(node)) {
            return constant;
        }

        return applied(node);
    };
}

export const applyMatcher = apply<ReturnType<ElementMatcher>>({ type: MatchType.NONE });
export const applyClearer = apply<ReturnType<ElementClearer>>(false);
///////

export interface SurroundFormat {
    matcher: ElementMatcher;
    ascender?: (node: FormattingNode, matchNode: MatchNode) => boolean; // TODO ascend beyond element or not?
    merger?: (before: FormattingNode, after: FormattingNode) => boolean; // TODO merge CN ranges or not? do not merge, if they are in differing match contexts
    formatter?: (node: FormattingNode) => boolean;
    surroundElement?: Element;
}
