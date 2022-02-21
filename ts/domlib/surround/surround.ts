// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { Matcher } from "../find-above";
import { findFarthest } from "../find-above";
import { apply, ApplyFormat, UnsurroundApplyFormat } from "./apply";
import { build, BuildFormat, UnsurroundBuildFormat } from "./build";
import { boolMatcher } from "./match-type";
import { splitPartiallySelected } from "./split-text";
import type { SurroundFormat } from "./surround-format";

function surroundInner<T>(
    node: Node,
    buildFormat: BuildFormat<T>,
    applyFormat: ApplyFormat<T>,
): Range {
    const forest = build(node, buildFormat);

    for (const tree of forest) {
        apply(tree, applyFormat);
    }

    return buildFormat.recreateRange();
}

function reformatInner<T>(
    range: Range,
    base: Element,
    build: BuildFormat<T>,
    evaluate: ApplyFormat<T>,
    matcher: Matcher,
): Range {
    const farthestMatchingAncestor = findFarthest(
        range.commonAncestorContainer,
        base,
        matcher,
    );

    if (farthestMatchingAncestor) {
        return surroundInner(farthestMatchingAncestor, build, evaluate);
    } else {
        return surroundInner(range.commonAncestorContainer, build, evaluate);
    }
}

/**
 * Assumes that there are no matching ancestor elements above
 * `range.commonAncestorContainer`. Make sure that the range is not placed
 * inside the format before using this.
 **/
export function surround<T>(
    range: Range,
    base: Element,
    format: SurroundFormat<T>,
): Range {
    const splitRange = splitPartiallySelected(range);
    const build = BuildFormat.make(format, base, range, splitRange);
    const evaluate = ApplyFormat.make(format);
    return surroundInner(range.commonAncestorContainer, build, evaluate);
}

export function reformat<T>(
    range: Range,
    base: Element,
    format: SurroundFormat<T>,
): Range {
    const splitRange = splitPartiallySelected(range);
    const build = BuildFormat.make(format, base, range, splitRange);
    const evaluate = ApplyFormat.make(format);
    return reformatInner(range, base, build, evaluate, boolMatcher(format));
}

export function unsurround<T>(
    range: Range,
    base: Element,
    format: SurroundFormat<T>,
): Range {
    const splitRange = splitPartiallySelected(range);
    const build = UnsurroundBuildFormat.make(format, base, range, splitRange);
    const evaluate = UnsurroundApplyFormat.make(format);
    return reformatInner(range, base, build, evaluate, boolMatcher(format));
}
