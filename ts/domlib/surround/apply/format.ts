// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { SurroundFormat } from "../surround-format";
import type { FormattingNode } from "../tree";

export class ApplyFormat<T> {
    constructor(protected readonly format: SurroundFormat<T>) {}

    static make<T>(format: SurroundFormat<T>): ApplyFormat<T> {
        return new ApplyFormat(format);
    }

    applyFormat(node: FormattingNode<T>): boolean {
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

export class UnsurroundApplyFormat<T> extends ApplyFormat<T> {
    static make<T>(format: SurroundFormat<T>): UnsurroundApplyFormat<T> {
        return new UnsurroundApplyFormat(format);
    }

    applyFormat(node: FormattingNode<T>): boolean {
        if (node.insideRange) {
            return false;
        }

        return super.applyFormat(node);
    }
}
