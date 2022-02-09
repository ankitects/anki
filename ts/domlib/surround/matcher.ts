// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { nodeIsCommonElement } from "../../lib/dom";

export enum MatchType {
    /**
     * An element unrelated to the surround format.
     * The value of NONE is 0, which allows for falsy tests.
     */
    NONE = 0,
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
    CLEAR,
    /** Element (or Text) is situated adjacent to a match */
    ALONG,
}

interface MatchNone {
    type: MatchType.NONE;
}

interface MatchMatch {
    type: MatchType.MATCH;
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

interface MatchAlong {
    type: MatchType.ALONG;
}

export type Match = MatchNone | MatchMatch | MatchClear;

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

export interface FoundMatch {
    match: MatchMatch | MatchClear;
    element: HTMLElement | SVGElement;
}

export interface FoundAlong {
    match: MatchAlong;
    element: Element | Text;
}

export type FoundAdjacent = FoundMatch | FoundAlong;

export interface SurroundFormat {
    surroundElement: Element;
    matcher: ElementMatcher;
}
