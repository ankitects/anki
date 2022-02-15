// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { FormattingNode, MatchNode } from "./tree-node";
import { nodeIsAmongNegligibles } from "./node-negligible";
import { nodeWithinRange } from "./within-range";
import { MatchType } from "./match-type";
import type { Match, SurroundFormat } from "./match-type";
import { nodeIsCommonElement } from "../../lib/dom";

export class ParseFormat {
    constructor(
        public readonly format: SurroundFormat,
        public readonly base: Element,
        public readonly range: Range,
    ) {}

    static make(format: SurroundFormat, base: Element, range: Range): ParseFormat {
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
        if (
            matchNode.isAscendable() &&
            matchNode.element !== this.base &&
            (!this.format.ascender || this.format.ascender(node, matchNode))
        ) {
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

export class UnsurroundParseFormat extends ParseFormat {
    static make(
        format: SurroundFormat,
        base: Element,
        range: Range,
    ): UnsurroundParseFormat {
        return new UnsurroundParseFormat(format, base, range);
    }

    tryMerge(before: FormattingNode, after: FormattingNode): FormattingNode | null {
        if (before.insideRange !== after.insideRange) {
            return null;
        }

        return super.tryMerge(before, after);
    }

    tryAscend(node: FormattingNode, matchNode: MatchNode): boolean {
        if (node.insideRange !== matchNode.insideRange) {
            false;
        }

        return super.tryAscend(node, matchNode);
    }
}
