// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { nodeIsCommonElement } from "../../lib/dom";

export enum MatchResult {
    /** Having this be 0 allows for falsy tests */
    NO_MATCH = 0,
    /** Element matches the predicate and may be removed */
    MATCH,
    /**
     * Element matches the predicate, but may not be removed.
     *
     * @remarks
     * This typically means that the element has other properties which prevent
     * it from being removed. E.g. an element matches a bold predicate, because
     * it has inline styling of `font-weight: bold`, but it also has other
     * inline styling aplied additionally.
     */
    KEEP,
    /** Element (or Text) is situated adjacent to a match */
    ALONG,
}

/**
 * A function to determine how an element relates to a element predicate.
 *
 * @remarks
 * Should be pure.
 *
 * @example
 * A predicate could be "is bold", which could match `b` and `strong` tags.
 */
export type ElementMatcher = (
    element: HTMLElement | SVGElement,
) => Exclude<MatchResult, MatchResult.ALONG>;

/**
 * A function applied to element that matched with MatchResult.KEEP.
 *
 * @remarks
 * Should be idempotent.
 */
export type ElementClearer = (element: HTMLElement | SVGElement) => boolean;

/**
 * We want to avoid users to have to deal with the difference between Element
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

export const applyMatcher = apply<ReturnType<ElementMatcher>>(MatchResult.NO_MATCH);
export const applyClearer = apply<ReturnType<ElementClearer>>(false);

export interface FoundMatch {
    element: HTMLElement | SVGElement;
    matchType: MatchResult.MATCH | MatchResult.KEEP;
}

export interface FoundAlong {
    element: Element | Text;
    matchType: MatchResult.ALONG;
}

export type FoundAdjacent = FoundMatch | FoundAlong;

export interface SurroundFormat {
    surroundElement: Element;
    matcher: ElementMatcher;
    clearer: ElementClearer;
}
