// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { Matcher } from "../find-above";
import { findFarthest } from "../find-above";
import { apply, ApplyFormat, ReformatApplyFormat, UnsurroundApplyFormat } from "./apply";
import { build, BuildFormat, ReformatBuildFormat, UnsurroundBuildFormat } from "./build";
import { boolMatcher } from "./match-type";
import { splitPartiallySelected } from "./split-text";
import type { SurroundFormat } from "./surround-format";

function surroundInner<T>(
    node: Node,
    buildFormat: BuildFormat<T>,
    applyFormat: ApplyFormat<T>,
): Range {
    const forest = build(node, buildFormat);
    apply(forest, applyFormat);
    return buildFormat.recreateRange();
}

function reformatInner<T>(
    range: Range,
    base: Element,
    build: BuildFormat<T>,
    apply: ApplyFormat<T>,
    matcher: Matcher,
): Range {
    const farthestMatchingAncestor = findFarthest(
        range.commonAncestorContainer,
        base,
        matcher,
    );

    if (farthestMatchingAncestor) {
        return surroundInner(farthestMatchingAncestor, build, apply);
    } else {
        return surroundInner(range.commonAncestorContainer, build, apply);
    }
}

/**
 * Assumes that there are no matching ancestor elements above
 * `range.commonAncestorContainer`. Make sure that the range is not placed
 * inside the format before using this.
 */
export function surround<T>(
    range: Range,
    base: Element,
    format: SurroundFormat<T>,
): Range {
    const splitRange = splitPartiallySelected(range);
    const build = new BuildFormat(format, base, range, splitRange);
    const apply = new ApplyFormat(format);
    return surroundInner(range.commonAncestorContainer, build, apply);
}

/**
 * Will not surround any unsurrounded text nodes in the range.
 */
export function reformat<T>(
    range: Range,
    base: Element,
    format: SurroundFormat<T>,
): Range {
    const splitRange = splitPartiallySelected(range);
    const build = new ReformatBuildFormat(format, base, range, splitRange);
    const apply = new ReformatApplyFormat(format);
    return reformatInner(range, base, build, apply, boolMatcher(format));
}

export function unsurround<T>(
    range: Range,
    base: Element,
    format: SurroundFormat<T>,
): Range {
    const splitRange = splitPartiallySelected(range);
    const build = new UnsurroundBuildFormat(format, base, range, splitRange);
    const apply = new UnsurroundApplyFormat(format);
    return reformatInner(range, base, build, apply, boolMatcher(format));
}
