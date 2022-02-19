// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { get } from "svelte/store";

import type { Matcher } from "../domlib/find-above";
import { findClosest } from "../domlib/find-above";
import type { SurroundFormat } from "../domlib/surround";
import { boolMatcher, reformat, surround, unsurround } from "../domlib/surround";
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
        : surround(range, base, format);

    selection.removeAllRanges();
    selection.addRange(surroundedRange);
}

function removeFormats(range: Range, formats: SurroundFormat[], base: Element): Range {
    let surroundRange = range;

    for (const format of formats) {
        surroundRange = unsurround(surroundRange, base, format);
    }

    return surroundRange;
}

export class Surrounder {
    private constructor() {}

    static make() {
        return new Surrounder();
    }

    private api: RichTextInputAPI | null = null;
    private trigger: any;

    set richText(api: RichTextInputAPI) {
        this.api = api;
        this.trigger = api.getTriggerOnNextInsert();
    }

    /**
     * After calling disable, using any of the surrounding methods will throw an
     * exception. Make sure to set the rich text before trying to use them again.
     */
    disable(): void {
        this.api = null;
        this.trigger = null;
    }

    private async _assert_base(): Promise<HTMLElement> {
        if (!this.api) {
            throw new Error("No rich text set");
        }

        return await this.api.element;
    }

    /**
     * Use the surround command on the current range of the RichTextInput.
     * If the range is already surrounded, it will unsurround instead.
     */
    async surround(
        format: SurroundFormat,
        exclusive: SurroundFormat[] = [],
    ): Promise<void> {
        const base = await this._assert_base();
        const selection = getSelection(base)!;
        const initialRange = getRange(selection);
        const matcher = boolMatcher(format);

        if (!initialRange) {
            return;
        } else if (initialRange.collapsed) {
            if (get(this.trigger.active)) {
                this.trigger.remove();
            } else {
                this.trigger.add(async ({ node }: { node: Node }) => {
                    initialRange.selectNode(node);

                    const matches = Boolean(findClosest(node, base, matcher));
                    const range = removeFormats(initialRange, exclusive, base);

                    surroundAndSelect(matches, range, base, format, selection);
                    selection.collapseToEnd();
                });
            }
        } else {
            const matches = isSurroundedInner(initialRange, base, matcher);
            const range = removeFormats(initialRange, exclusive, base);
            surroundAndSelect(matches, range, base, format, selection);
        }
    }

    /**
     * Use the surround command on the current range of the RichTextInput.
     * If the range is already surrounded, it will overwrite the format.
     * This might be better suited if the surrounding is parameterized (like
     * text color).
     */
    async overwriteSurround(
        format: SurroundFormat,
        exclusive: SurroundFormat[] = [],
    ): Promise<void> {
        const base = await this._assert_base();
        const selection = getSelection(base)!;
        const initialRange = getRange(selection);
        const matcher = boolMatcher(format);

        if (!initialRange) {
            return;
        } else if (initialRange.collapsed) {
            if (get(this.trigger.active)) {
                this.trigger.remove();
            } else {
                this.trigger.add(async ({ node }: { node: Node }): Promise<void> => {
                    initialRange.selectNode(node);

                    const range = removeFormats(initialRange, exclusive, base);
                    const matches = Boolean(findClosest(node, base, matcher));
                    surroundAndSelect(matches, range, base, format, selection);
                    selection.collapseToEnd();
                });
            }
        } else {
            const range = removeFormats(initialRange, exclusive, base);
            const surroundedRange = reformat(range, base, format);
            selection.removeAllRanges();
            selection.addRange(surroundedRange);
        }
    }

    /**
     * Check if the current selection is surrounded. A selection will count as
     * provided if either the start or the end boundary point are within the
     * provided format, OR if a surround trigger is active (surround on next
     * text insert).
     */
    async isSurrounded(format: SurroundFormat): Promise<boolean> {
        const base = await this._assert_base();
        const selection = getSelection(base)!;
        const range = getRange(selection);

        if (!range) {
            return false;
        }

        const isSurrounded = isSurroundedInner(range, base, boolMatcher(format));
        return get(this.trigger.active) ? !isSurrounded : isSurrounded;
    }

    /**
     * Clear the provided formats in the current range.
     */
    async remove(formats: SurroundFormat[]): Promise<void> {
        const base = await this._assert_base();
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
}

/**
 * @returns True, if element has no style attribute (anymore).
 */
export function removeEmptyStyle(element: HTMLElement | SVGElement): boolean {
    if (element.style.cssText.length === 0) {
        element.removeAttribute("style");
        // Calling `.hasAttribute` right after `.removeAttribute` might return true.
        return true;
    }

    return false;
}
