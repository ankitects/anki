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
    match: MatchRemove | MatchClear;
    element: HTMLElement | SVGElement;
}

// export type RangeMerger = (before: ChildNodeRange, after: ChildNodeRange) => boolean;

type Formatter = (range: Range) => void;

export interface SurroundFormat {
    matcher: ElementMatcher;
    ascender: (node: FormattingNode, matchNode: MatchNode) => boolean; // TODO ascend beyond element or not?
    merger: (before: FormattingNode, after: FormattingNode) => boolean; // TODO merge CN ranges or not? do not merge, if they are in differing match contexts
    formatter: Formatter; // TODO surround, or do nothing -> can decide difference between surrounding an unsurrounding; access to availableExclusiveParents
}

export interface SurroundFormatUser {
    matcher: SurroundFormat["matcher"];
    ascender?: (node: FormattingNode, matchNode: MatchNode) => boolean; // TODO ascend beyond element or not?
    merger?: (before: FormattingNode, after: FormattingNode) => boolean; // TODO merge CN ranges or not? do not merge, if they are in differing match contexts
    formatter?: SurroundFormat["formatter"];
    surroundElement?: Element;
}

import { FormattingNode, MatchNode } from "./tree-node";

function always() {
    return true;
}

export function userFormatToFormat(format: SurroundFormatUser): SurroundFormat {
    let formatter: Formatter;
    if (format.formatter) {
        formatter = format.formatter;
    } else if (format.surroundElement) {
        const element = format.surroundElement;
        formatter = (range: Range): void => range.surroundContents(element.cloneNode(false));
    } else {
        formatter = () => { /* noop */ };
    }

    const ascender = format.ascender ?? always;
    const merger = format.merger ?? always;

    return {
        matcher: format.matcher,
        ascender,
        merger,
        formatter,
    }
}

import { nodeIsAmongNegligibles } from "./node-negligible";
import { nodeWithinRange } from "./within-range";

export class ParseFormat {
    constructor(
        private format: SurroundFormatUser,
        private base: Element,
        private range: Range,
    ) {}

    static make(format: SurroundFormatUser, base: Element, range: Range) {
        return new ParseFormat(format, base, range);
    }

    matches(element: Element): Match {
        if (!nodeIsCommonElement(element)) {
            return { type: MatchType.NONE };
        }

        return this.format.matcher(element);
    }

    tryMerge(before: FormattingNode, after: FormattingNode): FormattingNode | null {
        if (!this.format.merger || this.format.merger(before, after)) {
            return FormattingNode.merge(before, after);
        }

        return null;
    }

    tryAscend(node: FormattingNode, matchNode: MatchNode): boolean {
        if (matchNode.element !== this.base && (!this.format.ascender || this.format.ascender(node, matchNode))) {
            node.ascendAbove(matchNode);
            return true;
        }

        return false;
    }

    mayExtend(element: Element): boolean {
        return element !== this.base && nodeIsAmongNegligibles(element);
    }

    isInsideRange(node: Node): boolean {
        return nodeWithinRange(node, this.range);
    }
}
