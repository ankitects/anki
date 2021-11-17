// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

export enum MatchResult {
    NO_MATCH,
    MATCH,
    /* Element matches the predicate and may be removed */
    KEEP,
    /* Element matches the predicate, but may not be removed
     * This typically means that the element has other properties which prevent it from being removed */
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
