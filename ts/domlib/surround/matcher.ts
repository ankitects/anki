// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

export enum MatchResult {
    /* Having this be 0 allows for falsy tests */
    NO_MATCH = 0,
    /* Element matches the predicate and may be removed */
    MATCH,
    /* Element matches the predicate, but may not be removed
     * This typically means that the element has other properties which prevent it from being removed */
    KEEP,
}

/**
 * Should be pure
 */
export type ElementMatcher = (element: Element) => MatchResult;

/**
 * Is applied to values that match with KEEP
 * Should be idempotent
 */
export type ElementClearer = (element: Element) => boolean;

export const matchTagName =
    (tagName: string) =>
    (element: Element): MatchResult => {
        return element.matches(tagName) ? MatchResult.MATCH : MatchResult.NO_MATCH;
    };

export interface FoundMatch {
    element: Element;
    matchType: Exclude<MatchResult, MatchResult.NO_MATCH>;
}
