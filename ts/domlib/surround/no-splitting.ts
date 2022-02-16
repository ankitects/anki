// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { Matcher } from "../find-above";
import { findFarthest } from "../find-above";
import { build } from "./build-tree";
import { EvaluateFormat, UnsurroundEvaluateFormat } from "./evaluate-format";
import type { SurroundFormat } from "./match-type";
import { ParseFormat, UnsurroundParseFormat } from "./parse-format";
import { splitPartiallySelected } from "./split-text";

export function surroundInner(
    range: Range,
    parse: ParseFormat,
    evaluate: EvaluateFormat,
): Range {
    build(range.commonAncestorContainer, parse, evaluate, false);
    return evaluate.recreateRange();
}

export function surround(range: Range, base: Element, format: SurroundFormat): Range {
    const splitRange = splitPartiallySelected(range);
    const parse = ParseFormat.make(format, base, range);
    const evaluate = EvaluateFormat.make(format, splitRange);

    return surroundInner(range, parse, evaluate);
}

function reformatInner(
    range: Range,
    base: Element,
    parse: ParseFormat,
    evaluate: EvaluateFormat,
    matcher: Matcher,
): Range {
    const farthestMatchingAncestor = findFarthest(
        range.commonAncestorContainer,
        base,
        matcher,
    );

    if (farthestMatchingAncestor) {
        build(farthestMatchingAncestor, parse, evaluate, true);
    } else {
        return surroundInner(range, parse, evaluate);
    }

    return evaluate.recreateRange();
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
export function reformat(range: Range, base: Element, format: SurroundFormat): Range {
    const splitRange = splitPartiallySelected(range);
    const parse = ParseFormat.make(format, base, range);
    const evaluate = EvaluateFormat.make(format, splitRange);

    function matcher(element: Element): boolean {
        return Boolean(format.matcher(element as HTMLElement | SVGElement).type);
    }

    return reformatInner(range, base, parse, evaluate, matcher);
}

/**
 * The counterpart to `surround`
 **/
export function unsurround(range: Range, base: Element, format: SurroundFormat): Range {
    const splitRange = splitPartiallySelected(range);
    const parse = UnsurroundParseFormat.make(format, base, range);
    const evaluate = UnsurroundEvaluateFormat.make(format, splitRange);

    function matcher(element: Element): boolean {
        return Boolean(format.matcher(element as HTMLElement | SVGElement).type);
    }

    return reformatInner(range, base, parse, evaluate, matcher);
}
