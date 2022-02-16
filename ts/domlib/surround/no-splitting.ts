// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { findFarthest } from "../find-above";
import { build } from "./build-tree";
import { EvaluateFormat } from "./evaluate-format";
import type { SurroundFormat } from "./match-type";
import { ParseFormat } from "./parse-format";
import { splitPartiallySelected } from "./split-text";

function surround(
    node: Node,
    parseFormat: ParseFormat,
    evaluateFormat: EvaluateFormat,
): void {
    build(node, parseFormat, evaluateFormat, false);
}

export function reformatRange(
    range: Range,
    parseFormat: ParseFormat,
    evaluateFormat: EvaluateFormat,
): Range {
    const { start, end } = splitPartiallySelected(range);

    const farthestMatchingAncestor = findFarthest(
        range.commonAncestorContainer,
        parseFormat.base,
        (element: Element): boolean =>
            Boolean(
                parseFormat.format.matcher(element as HTMLElement | SVGElement).type,
            ),
    );

    if (!farthestMatchingAncestor) {
        surround(range.commonAncestorContainer, parseFormat, evaluateFormat);

        const surroundedRange = new Range();
        surroundedRange.setStartBefore(start!);
        surroundedRange.setEndAfter(end!);
        range.commonAncestorContainer.normalize();

        return surroundedRange;
    }

    build(farthestMatchingAncestor, parseFormat, evaluateFormat, true);

    const surroundedRange = new Range();
    surroundedRange.setStartBefore(start!);
    surroundedRange.setEndAfter(end!);
    farthestMatchingAncestor.normalize();

    return surroundedRange;
}

/**
 * Avoids splitting existing elements in the surrounded area. Might create
 * multiple of the surrounding element and remove elements specified by matcher.
 * Can be used for inline elements e.g. <b>, or <strong>.
 *
 * @param range: The range to surround
 * @param base: Surrounding will not ascent beyond this point; base.contains(range.commonAncestorContainer) should be true
 * @param format: Specifies the type of surrounding.
 **/
export function surroundNoSplitting(
    range: Range,
    base: Element,
    format: SurroundFormat,
): Range {
    const { start, end } = splitPartiallySelected(range);

    surround(
        range.commonAncestorContainer,
        ParseFormat.make(format, base, range),
        EvaluateFormat.make(format),
    );

    const surroundedRange = new Range();
    surroundedRange.setStartBefore(start);
    surroundedRange.setEndAfter(end);
    base.normalize();

    return surroundedRange;
}
