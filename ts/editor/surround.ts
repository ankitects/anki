// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { get } from "svelte/store";

import type { Matcher } from "../domlib/find-above";
import { findClosest } from "../domlib/find-above";
import type { SurroundFormat } from "../domlib/surround";
import { boolMatcher, reformat, surround, unsurround } from "../domlib/surround";
import { getRange, getSelection } from "../lib/cross-browser";
import { registerPackage } from "../lib/runtime-require";
import type { TriggerItem } from "../sveltelib/handler-list";
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

function surroundAndSelect<T>(
    matches: boolean,
    range: Range,
    base: HTMLElement,
    format: SurroundFormat<T>,
    selection: Selection,
): void {
    const surroundedRange = matches
        ? unsurround(range, base, format)
        : surround(range, base, format);

    selection.removeAllRanges();
    selection.addRange(surroundedRange);
}

function removeFormats(
    range: Range,
    base: Element,
    formats: SurroundFormat[],
    reformats: SurroundFormat[] = [],
): Range {
    let surroundRange = range;

    for (const format of formats) {
        surroundRange = unsurround(surroundRange, base, format);
    }

    for (const format of reformats) {
        surroundRange = reformat(surroundRange, base, format);
    }

    return surroundRange;
}

export class Surrounder {
    static make(): Surrounder {
        return new Surrounder();
    }

    private api: RichTextInputAPI | null = null;
    private trigger: TriggerItem<{ event: InputEvent; text: Text }> | null = null;

    set richText(api: RichTextInputAPI) {
        this.api = api;
        this.trigger = api.inputHandler.insertText.trigger({ once: true });
    }

    /**
     * After calling disable, using any of the surrounding methods will throw an
     * exception. Make sure to set the rich text before trying to use them again.
     */
    disable(): void {
        this.api = null;
        this.trigger?.off();
        this.trigger = null;
    }

    private async _assert_base(): Promise<HTMLElement> {
        if (!this.api) {
            throw new Error("No rich text set");
        }

        return await this.api.element;
    }

    private _toggleTrigger<T>(
        base: HTMLElement,
        selection: Selection,
        matcher: Matcher,
        format: SurroundFormat<T>,
        exclusive: SurroundFormat<T>[] = [],
    ): void {
        if (get(this.trigger!.active)) {
            this.trigger!.off();
        } else {
            this.trigger!.on(async ({ text }) => {
                const range = new Range();
                range.selectNode(text);

                const matches = Boolean(findClosest(text, base, matcher));
                const clearedRange = removeFormats(range, base, exclusive);
                surroundAndSelect(matches, clearedRange, base, format, selection);
                selection.collapseToEnd();
            });
        }
    }

    private _toggleTriggerOverwrite<T>(
        base: HTMLElement,
        selection: Selection,
        format: SurroundFormat<T>,
        exclusive: SurroundFormat<T>[] = [],
    ): void {
        this.trigger!.on(async ({ text }) => {
            const range = new Range();
            range.selectNode(text);

            const clearedRange = removeFormats(range, base, exclusive);
            const surroundedRange = surround(clearedRange, base, format);
            selection.removeAllRanges();
            selection.addRange(surroundedRange);
            selection.collapseToEnd();
        });
    }

    private _toggleTriggerRemove<T>(
        base: HTMLElement,
        selection: Selection,
        remove: SurroundFormat<T>[],
        reformat: SurroundFormat<T>[] = [],
    ): void {
        this.trigger!.on(async ({ text }) => {
            const range = new Range();
            range.selectNode(text);

            const clearedRange = removeFormats(range, base, remove, reformat);
            selection.removeAllRanges();
            selection.addRange(clearedRange);
            selection.collapseToEnd();
        });
    }

    /**
     * Use the surround command on the current range of the RichTextInput.
     * If the range is already surrounded, it will unsurround instead.
     */
    async surround<T>(
        format: SurroundFormat<T>,
        exclusive: SurroundFormat<T>[] = [],
    ): Promise<void> {
        const base = await this._assert_base();
        const selection = getSelection(base)!;
        const range = getRange(selection);
        const matcher = boolMatcher(format);

        if (!range) {
            return;
        }

        if (range.collapsed) {
            return this._toggleTrigger(base, selection, matcher, format, exclusive);
        }

        const clearedRange = removeFormats(range, base, exclusive);
        const matches = isSurroundedInner(clearedRange, base, matcher);
        surroundAndSelect(matches, clearedRange, base, format, selection);
    }

    /**
     * Use the surround command on the current range of the RichTextInput.
     * If the range is already surrounded, it will overwrite the format.
     * This might be better suited if the surrounding is parameterized (like
     * text color).
     */
    async overwriteSurround<T>(
        format: SurroundFormat<T>,
        exclusive: SurroundFormat<T>[] = [],
    ): Promise<void> {
        const base = await this._assert_base();
        const selection = getSelection(base)!;
        const range = getRange(selection);

        if (!range) {
            return;
        }

        if (range.collapsed) {
            return this._toggleTriggerOverwrite(base, selection, format, exclusive);
        }

        const clearedRange = removeFormats(range, base, exclusive);
        const surroundedRange = surround(clearedRange, base, format);
        selection.removeAllRanges();
        selection.addRange(surroundedRange);
    }

    /**
     * Check if the current selection is surrounded. A selection will count as
     * provided if either the start or the end boundary point are within the
     * provided format, OR if a surround trigger is active (surround on next
     * text insert).
     */
    async isSurrounded<T>(format: SurroundFormat<T>): Promise<boolean> {
        const base = await this._assert_base();
        const selection = getSelection(base)!;
        const range = getRange(selection);

        if (!range) {
            return false;
        }

        const isSurrounded = isSurroundedInner(range, base, boolMatcher(format));
        return get(this.trigger!.active) ? !isSurrounded : isSurrounded;
    }

    /**
     * Clear/Reformat the provided formats in the current range.
     */
    async remove<T>(
        formats: SurroundFormat<T>[],
        reformats: SurroundFormat<T>[] = [],
    ): Promise<void> {
        const base = await this._assert_base();
        const selection = getSelection(base)!;
        const range = getRange(selection);

        if (!range) {
            return;
        }

        if (range.collapsed) {
            return this._toggleTriggerRemove(base, selection, formats, reformats);
        }

        const surroundedRange = removeFormats(range, base, formats, reformats);
        selection.removeAllRanges();
        selection.addRange(surroundedRange);
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

registerPackage("anki/surround", {
    Surrounder,
});
