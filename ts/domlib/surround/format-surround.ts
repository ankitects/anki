// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { ElementNode,FormattingNode } from "./formatting-tree";
import type { MatchType } from "./match-type";

export type ElementMatcher = (
    element: HTMLElement | SVGElement,
    match: MatchType,
) => void;

export interface SurroundFormat {
    matcher: ElementMatcher;
    /**
     * @returns Whether node is allowed to ascend beyond elementNode.
     */
    ascender?: (node: FormattingNode, elementNode: ElementNode) => boolean;
    /**
     * @returns Whehter before or after are allowed to merge to a single
     * FormattingNode range
     */
    merger?: (before: FormattingNode, after: FormattingNode) => boolean;
    formatter?: (node: FormattingNode) => boolean;
    surroundElement?: Element;
}
