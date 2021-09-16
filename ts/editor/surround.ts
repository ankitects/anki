// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { RichTextInputAPI } from "./RichTextInput.svelte";
import { getSelection } from "../lib/cross-browser";
import { surroundNoSplitting, unsurround, findClosest } from "../lib/surround";
import type { ElementMatcher, ElementClearer } from "../lib/surround";

export function isSurroundedInner(
    range: AbstractRange,
    base: HTMLElement,
    matcher: ElementMatcher,
): boolean {
    return Boolean(
        findClosest(range.startContainer, base, matcher) ||
            findClosest(range.endContainer, base, matcher),
    );
}

export async function isSurrounded(
    input: RichTextInputAPI,
    matcher: ElementMatcher,
): Promise<boolean> {
    const base = await input.element;
    const selection = getSelection(base)!;
    const range = selection.getRangeAt(0);

    return isSurroundedInner(range, base, matcher);
}

function surroundAndSelect(
    matches: boolean,
    range: Range,
    selection: Selection,
    surroundElement: Element,
    base: HTMLElement,
    matcher: ElementMatcher,
    clearer: ElementClearer,
): void {
    const { surroundedRange } = matches
        ? unsurround(range, surroundElement, base, matcher, clearer)
        : surroundNoSplitting(range, surroundElement, base, matcher, clearer);

    selection.removeAllRanges();
    selection.addRange(surroundedRange);
}

export async function surroundCommand(
    input: RichTextInputAPI,
    surroundElement: Element,
    matcher: ElementMatcher,
    clearer: ElementClearer = () => false,
): Promise<void> {
    const base = await input.element;
    const selection = getSelection(base)!;
    const range = selection.getRangeAt(0);

    if (range.collapsed) {
        input.triggerOnInsert(async ({ node }): Promise<void> => {
            range.selectNode(node);

            const matches = Boolean(findClosest(node, base, matcher));
            surroundAndSelect(
                matches,
                range,
                selection,
                surroundElement,
                base,
                matcher,
                clearer,
            );

            selection.collapseToEnd();
        });
    } else {
        const matches = isSurroundedInner(range, base, matcher);
        surroundAndSelect(
            matches,
            range,
            selection,
            surroundElement,
            base,
            matcher,
            clearer,
        );
    }
}
