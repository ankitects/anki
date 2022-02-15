// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { SurroundFormat } from "./match-type";
import type { FormattingNode } from "./tree-node";

export class EvaluateFormat {
    constructor(protected format: SurroundFormat) {}

    static make(format: SurroundFormat): EvaluateFormat {
        return new EvaluateFormat(format);
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
}

export class UnsurroundEvaluateFormat extends EvaluateFormat {
    static make(format: SurroundFormat): UnsurroundEvaluateFormat {
        return new UnsurroundEvaluateFormat(format);
    }

    applyFormat(node: FormattingNode): boolean {
        if (node.insideRange) {
            return false;
        }

        return super.applyFormat(node);
    }
}
