// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { get } from "svelte/store";

import type { ElementMatcher, SurroundFormat } from "../domlib/surround";
import { surroundNoSplitting, unsurround } from "../domlib/surround";
import { findClosest } from "../domlib/find-above";
import type { Matcher } from "../domlib/find-above";
import { getRange, getSelection } from "../lib/cross-browser";
import type { RichTextInputAPI } from "./rich-text-input";

function isSurroundedInner(
    range: AbstractRange,
    base: HTMLElement,
    matcher: Matcher,
): boolean {
    return Boolean(
        findClosest(range.startContainer, base, matcher) ||
            findClosest(range.endContainer, base, matcher),
    );
}

function surroundAndSelect(
    matches: boolean,
    range: Range,
    base: HTMLElement,
    format: SurroundFormat,
    selection: Selection,
): void {
    const surroundedRange = matches
        ? unsurround(range, base, format)
        : surroundNoSplitting(range, base, format);

    selection.removeAllRanges();
    selection.addRange(surroundedRange);
}

export interface GetSurrounderResult {
    surroundCommand(
        format: SurroundFormat,
        mutualExclusiveFormats?: SurroundFormat[],
    ): Promise<void>;
    isSurrounded(matcher: ElementMatcher): Promise<boolean>;
}


function wrapMatcher(matcher: ElementMatcher): Matcher {
    return (element: Element): boolean => Boolean(matcher(element as HTMLElement | SVGElement).type)
}

/**
 * A convenience function supposed to create some common formatting functions, e.g. bold, italic, etc.
 */
export function getSurrounder(richTextInput: RichTextInputAPI): GetSurrounderResult {
    const trigger = richTextInput.getTriggerOnNextInsert();

    async function isSurrounded(matcher: ElementMatcher): Promise<boolean> {
        const base = await richTextInput.element;
        const selection = getSelection(base)!;
        const range = getRange(selection);

        if (!range) {
            return false;
        }

        const isSurrounded = isSurroundedInner(range, base, wrapMatcher(matcher));
        return get(trigger.active) ? !isSurrounded : isSurrounded;
    }

    async function surroundCommand(
        format: SurroundFormat,
        mutualExclusiveFormats: SurroundFormat[] = [],
    ): Promise<void> {
        const base = await richTextInput.element;
        const selection = getSelection(base)!;
        const initialRange = getRange(selection);
        const matcher = wrapMatcher(format.matcher);

        if (!initialRange) {
            return;
        } else if (initialRange.collapsed) {
            if (get(trigger.active)) {
                trigger.remove();
            } else {
                trigger.add(async ({ node }: { node: Node }) => {
                    initialRange.selectNode(node);

                    const matches = Boolean(findClosest(node, base, matcher));
                    const range = removeFormats(
                        initialRange,
                        mutualExclusiveFormats,
                        base,
                    );

                    surroundAndSelect(matches, range, base, format, selection);
                    selection.collapseToEnd();
                });
            }
        } else {
            const matches = isSurroundedInner(initialRange, base, matcher);
            const range = removeFormats(initialRange, mutualExclusiveFormats, base);

            surroundAndSelect(matches, range, base, format, selection);
        }
    }

    return {
        surroundCommand,
        isSurrounded,
    };
}

function removeFormats(range: Range, formats: SurroundFormat[], base: Element): Range {
    let surroundRange = range;

    for (const format of formats) {
        surroundRange = unsurround(surroundRange, base, format);
    }

    return surroundRange;
}

interface RemoveFormatResult {
    removeFormat(formats: SurroundFormat[]): Promise<void>;
}

export function getRemoveFormat(richTextInput: RichTextInputAPI): RemoveFormatResult {
    async function removeFormat(formats: SurroundFormat[]): Promise<void> {
        const base = await richTextInput.element;
        const selection = getSelection(base)!;
        const range = getRange(selection);

        if (!range || range.collapsed) {
            return;
        } else {
            const surroundedRange = removeFormats(range, formats, base);
            selection.removeAllRanges();
            selection.addRange(surroundedRange);
        }
    }

    return {
        removeFormat,
    };
}

/**
 * @returns True, if an empty style attribute was removed
 */
export function removeEmptyStyle(element: HTMLElement | SVGElement): boolean {
    if (element.style.cssText.length === 0) {
        element.removeAttribute("style");
    }

    return element.hasAttribute("style");
}
