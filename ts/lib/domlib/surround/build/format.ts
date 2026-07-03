// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { elementIsBlock } from "@tslib/dom";

import { Position } from "../../location";
import { Match } from "../match-type";
import type { SplitRange } from "../split-text";
import type { SurroundFormat } from "../surround-format";
import type { ElementNode } from "../tree";
import { FormattingNode } from "../tree";

function nodeWithinRange(node: Node, range: Range): boolean {
    const nodeRange = new Range();
    nodeRange.selectNodeContents(node);

    return (
        range.compareBoundaryPoints(Range.START_TO_START, nodeRange)
            !== Position.After
        && range.compareBoundaryPoints(Range.END_TO_END, nodeRange) !== Position.Before
    );
}

/**
 * Takes user-provided functions as input, to modify certain parts of the algorithm.
 */
export class BuildFormat<T> {
    constructor(
        public readonly format: SurroundFormat<T>,
        public readonly base: Element,
        public readonly range: Range,
        public readonly splitRange: SplitRange,
    ) {}

    createMatch(element: Element): Match<T> {
        const match = new Match<T>();
        this.format.matcher(element as HTMLElement | SVGElement, match);
        return match;
    }

    tryMerge(
        before: FormattingNode<T>,
        after: FormattingNode<T>,
    ): FormattingNode<T> | null {
        if (!this.format.merger || this.format.merger(before, after)) {
            return FormattingNode.merge(before, after);
        }

        return null;
    }

    tryAscend(node: FormattingNode<T>, elementNode: ElementNode): boolean {
        if (!elementIsBlock(elementNode.element) && elementNode.element !== this.base) {
            node.ascendAbove(elementNode);
            return true;
        }

        return false;
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

export class UnsurroundBuildFormat<T> extends BuildFormat<T> {
    tryMerge(
        before: FormattingNode<T>,
        after: FormattingNode<T>,
    ): FormattingNode<T> | null {
        if (before.insideRange !== after.insideRange) {
            return null;
        }

        return super.tryMerge(before, after);
    }
}

export class ReformatBuildFormat<T> extends BuildFormat<T> {
    tryMerge(
        before: FormattingNode<T>,
        after: FormattingNode<T>,
    ): FormattingNode<T> | null {
        if (before.hasMatch !== after.hasMatch) {
            return null;
        }

        return super.tryMerge(before, after);
    }
}
