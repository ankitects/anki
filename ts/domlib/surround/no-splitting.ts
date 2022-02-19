// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { Matcher } from "../find-above";
import { findFarthest } from "../find-above";
import { build } from "./build-tree";
import { EvaluateFormat, UnsurroundEvaluateFormat } from "./format-evaluate";
import { ParseFormat, UnsurroundParseFormat } from "./format-parse";
import type { SurroundFormat } from "./format-surround";
import { FakeMatch } from "./match-type";
import { splitPartiallySelected } from "./split-text";

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
    return build(range.commonAncestorContainer, parse, evaluate);
}

export function surround(range: Range, base: Element, format: SurroundFormat): Range {
    const splitRange = splitPartiallySelected(range);
    const parse = ParseFormat.make(format, base, range, splitRange);
    const evaluate = EvaluateFormat.make(format);

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
        return build(farthestMatchingAncestor, parse, evaluate);
    } else {
        return surroundInner(range, parse, evaluate);
    }
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
    const parse = ParseFormat.make(format, base, range, splitRange);
    const evaluate = EvaluateFormat.make(format);
    return reformatInner(range, base, parse, evaluate, boolMatcher(format));
}

export function unsurround(range: Range, base: Element, format: SurroundFormat): Range {
    const splitRange = splitPartiallySelected(range);
    const parse = UnsurroundParseFormat.make(format, base, range, splitRange);
    const evaluate = UnsurroundEvaluateFormat.make(format);
    return reformatInner(range, base, parse, evaluate, boolMatcher(format));
}
