// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { elementIsBlock } from "../../lib/dom";
import { Position } from "../location";
import type { SurroundFormat } from "./format-surround";
import { ElementNode,FormattingNode } from "./formatting-tree";
import { Match } from "./match-type";
import type { SplitRange } from "./split-text";

function nodeWithinRange(node: Node, range: Range): boolean {
    const nodeRange = new Range();
    nodeRange.selectNodeContents(node);

    return (
        range.compareBoundaryPoints(Range.START_TO_START, nodeRange) !==
            Position.After &&
        range.compareBoundaryPoints(Range.END_TO_END, nodeRange) !== Position.Before
    );
}

export class ParseFormat {
    constructor(
        public readonly format: SurroundFormat,
        public readonly base: Element,
        public readonly range: Range,
        public readonly splitRange: SplitRange,
    ) {}

    static make(
        format: SurroundFormat,
        base: Element,
        range: Range,
        splitRange: SplitRange,
    ): ParseFormat {
        return new ParseFormat(format, base, range, splitRange);
    }

    createMatch(element: Element): Match {
        const match = new Match();
        this.format.matcher(element as HTMLElement | SVGElement, match);
        return match;
    }

    tryMerge(before: FormattingNode, after: FormattingNode): FormattingNode | null {
        if (!this.format.merger || this.format.merger(before, after)) {
            return FormattingNode.merge(before, after);
        }

        return null;
    }

    tryAscend(node: FormattingNode, elementNode: ElementNode): boolean {
        if (
            elementNode.isAscendable() &&
            elementNode.element !== this.base &&
            (!this.format.ascender || this.format.ascender(node, elementNode))
        ) {
            node.ascendAbove(elementNode);
            return true;
        }

        return false;
    }

    mayExtend(element: Element): boolean {
        return !elementIsBlock(element) && element !== this.base;
    }

    isInsideRange(node: Node): boolean {
        return nodeWithinRange(node, this.range);
    }

    announceElementRemoval(element: Element): void {
        this.splitRange.adjustRange(element);
    }

    recreateRange(): Range {
        return this.splitRange.toDOMRange();
    }
}

export class UnsurroundParseFormat extends ParseFormat {
    static make(
        format: SurroundFormat,
        base: Element,
        range: Range,
        splitRange: SplitRange,
    ): UnsurroundParseFormat {
        return new UnsurroundParseFormat(format, base, range, splitRange);
    }

    tryMerge(before: FormattingNode, after: FormattingNode): FormattingNode | null {
        if (before.insideRange !== after.insideRange) {
            return null;
        }

        return super.tryMerge(before, after);
    }

    tryAscend(node: FormattingNode, elementNode: ElementNode): boolean {
        if (node.insideRange !== elementNode.insideRange) {
            false;
        }

        return super.tryAscend(node, elementNode);
    }
}
