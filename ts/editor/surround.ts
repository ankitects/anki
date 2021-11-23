// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { get } from "svelte/store";
import { getSelection, getRange } from "../lib/cross-browser";
import { surroundNoSplitting, unsurround, findClosest } from "../domlib/surround";
import type { ElementMatcher, ElementClearer } from "../domlib/surround";
import type { RichTextInputAPI } from "./RichTextInput.svelte";

function isSurroundedInner(
    range: AbstractRange,
    base: HTMLElement,
    matcher: ElementMatcher,
): boolean {
    return Boolean(
        findClosest(range.startContainer, base, matcher) ||
            findClosest(range.endContainer, base, matcher),
    );
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

export interface GetSurrounderResult {
    surroundCommand(
        surroundElement: Element,
        matcher: ElementMatcher,
        clearer?: ElementClearer,
    ): Promise<void>;
    isSurrounded(matcher: ElementMatcher): Promise<boolean>;
}

export function getSurrounder(richTextInput: RichTextInputAPI): GetSurrounderResult {
    const { add, remove, active } = richTextInput.getTriggerOnNextInsert();

    async function isSurrounded(matcher: ElementMatcher): Promise<boolean> {
        const base = await richTextInput.element;
        const selection = getSelection(base)!;
        const range = getRange(selection);

        if (!range) {
            return false;
        }

        const isSurrounded = isSurroundedInner(range, base, matcher);
        return get(active) ? !isSurrounded : isSurrounded;
    }

    async function surroundCommand(
        surroundElement: Element,
        matcher: ElementMatcher,
        clearer: ElementClearer = () => false,
    ): Promise<void> {
        const base = await richTextInput.element;
        const selection = getSelection(base)!;
        const range = getRange(selection);

        if (!range) {
            return;
        } else if (range.collapsed) {
            if (get(active)) {
                remove();
            } else {
                add(async ({ node }: { node: Node }) => {
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
            }
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

    return {
        surroundCommand,
        isSurrounded,
    };
}
