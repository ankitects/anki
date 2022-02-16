// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { FormattingNode } from "./formatting-tree";
import type { SurroundFormat } from "./match-type";
import type { SplitRange } from "./split-text";

export class EvaluateFormat {
    constructor(
        protected readonly format: SurroundFormat,
        protected readonly range: SplitRange,
    ) {}

    static make(format: SurroundFormat, range: SplitRange): EvaluateFormat {
        return new EvaluateFormat(format, range);
    }

    applyFormat(node: FormattingNode): boolean {
        if (this.format.surroundElement) {
            node.range
                .toDOMRange()
                .surroundContents(this.format.surroundElement.cloneNode(false));
            return true;
        } else if (this.format.formatter) {
            return this.format.formatter(node);
        }

        return false;
    }

    announceElementRemoval(element: Element): void {
        this.range.adjustRange(element);
    }

    recreateRange(): Range {
        return this.range.recreateRange();
    }
}

export class UnsurroundEvaluateFormat extends EvaluateFormat {
    static make(format: SurroundFormat, range: SplitRange): UnsurroundEvaluateFormat {
        return new UnsurroundEvaluateFormat(format, range);
    }

    applyFormat(node: FormattingNode): boolean {
        if (node.insideRange) {
            return false;
        }

        return super.applyFormat(node);
    }
}
