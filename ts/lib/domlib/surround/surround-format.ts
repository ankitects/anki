// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { MatchType } from "./match-type";
import type { FormattingNode } from "./tree";

export interface SurroundFormat<T = never> {
    /**
     * Determine whether element matches the format. Confirm by calling
     * `match.remove` or `match.clear`. Sustain parameters provided to the format
     * by calling `match.setCache`.
     */
    matcher: (element: HTMLElement | SVGElement, match: MatchType<T>) => void;
    /**
     * @returns Whether before or after are allowed to merge to a single
     * FormattingNode range
     */
    merger?: (before: FormattingNode<T>, after: FormattingNode<T>) => boolean;
    /**
     * Apply according to this formatter.
     *
     * @returns Whether formatter added a new element around the range.
     */
    formatter?: (node: FormattingNode<T>) => boolean;
    /**
     * Surround with this node as formatting. Shorthand alternative to `formatter`.
     */
    surroundElement?: Element;
}
