// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { Matcher } from "../find-above";
import { findFarthest } from "../find-above";
import { build } from "./build-tree";
import { EvaluateFormat, UnsurroundEvaluateFormat } from "./format-evaluate";
import { ParseFormat, UnsurroundParseFormat } from "./format-parse";
import { splitPartiallySelected } from "./split-text";
import { FakeMatch } from "./match-type";
import type { SurroundFormat } from "./format-surround";

export function boolMatcher(format: SurroundFormat): (element: Element) => boolean {
    return function (element: Element): boolean {
        const fake = new FakeMatch();
        format.matcher(element as HTMLElement | SVGElement, fake);
        return fake.value;
    };
}

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
    return reformatInner(range, base, parse, evaluate, boolMatcher(format));
}

export function unsurround(range: Range, base: Element, format: SurroundFormat): Range {
    const splitRange = splitPartiallySelected(range);
    const parse = UnsurroundParseFormat.make(format, base, range);
    const evaluate = UnsurroundEvaluateFormat.make(format, splitRange);
    return reformatInner(range, base, parse, evaluate, boolMatcher(format));
}
