// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { Writable } from "svelte/store";
import { get, writable } from "svelte/store";

import type { Matcher } from "../domlib/find-above";
import { findClosest } from "../domlib/find-above";
import type { SurroundFormat } from "../domlib/surround";
import { boolMatcher, reformat, surround, unsurround } from "../domlib/surround";
import { getRange, getSelection } from "../lib/cross-browser";
import { registerPackage } from "../lib/runtime-require";
import type { TriggerItem } from "../sveltelib/handler-list";
import type { InputHandlerAPI } from "../sveltelib/input-handler";

function isValid<T>(value: T | undefined): value is T {
    return Boolean(value);
}

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

export interface SurroundedAPI {
    element: Promise<HTMLElement>;
    inputHandler: InputHandlerAPI;
}

export class Surrounder<T = unknown> {
    static make<T>(): Surrounder<T> {
        return new Surrounder();
    }

    #api: SurroundedAPI | null = null;
    #triggers: Map<string, TriggerItem<{ event: InputEvent; text: Text }>> =
        new Map();

    active: Writable<boolean> = writable(false);

    enable(api: SurroundedAPI): void {
        this.#api = api;
        this.active.set(true);

        for (const key of this.#formats.keys()) {
            this.#triggers.set(
                key,
                this.#api.inputHandler.insertText.trigger({ once: true }),
            );
        }
    }

    /**
     * After calling disable, using any of the surrounding methods will throw an
     * exception. Make sure to set the input before trying to use them again.
     */
    disable(): void {
        this.#api = null;
        this.active.set(false);

        for (const [key, trigger] of this.#triggers) {
            trigger.off();
            this.#triggers.delete(key);
        }
    }

    async #assert_base(): Promise<HTMLElement> {
        if (!this.#api) {
            throw new Error("Surrounder: No input set");
        }

        return this.#api.element;
    }

    #toggleTrigger<T>(
        base: HTMLElement,
        selection: Selection,
        matcher: Matcher,
        format: SurroundFormat<T>,
        trigger: TriggerItem<{ event: InputEvent; text: Text }>,
        exclusive: SurroundFormat<T>[] = [],
    ): void {
        if (get(trigger.active)) {
            trigger.off();
        } else {
            trigger.on(async ({ text }) => {
                const range = new Range();
                range.selectNode(text);

                const matches = Boolean(findClosest(text, base, matcher));
                const clearedRange = removeFormats(range, base, exclusive);
                surroundAndSelect(matches, clearedRange, base, format, selection);
                selection.collapseToEnd();
            });
        }
    }

    #toggleTriggerOverwrite<T>(
        base: HTMLElement,
        selection: Selection,
        format: SurroundFormat<T>,
        trigger: TriggerItem<{ event: InputEvent; text: Text }>,
        exclusive: SurroundFormat<T>[] = [],
    ): void {
        trigger.on(async ({ text }) => {
            const range = new Range();
            range.selectNode(text);

            const clearedRange = removeFormats(range, base, exclusive);
            const surroundedRange = surround(clearedRange, base, format);
            selection.removeAllRanges();
            selection.addRange(surroundedRange);
            selection.collapseToEnd();
        });
    }

    #toggleTriggerRemove<T>(
        base: HTMLElement,
        selection: Selection,
        remove: SurroundFormat<T>[],
        triggers: TriggerItem<{ event: InputEvent; text: Text }>[],
        reformat: SurroundFormat<T>[] = [],
    ): void {
        triggers.map((trigger) =>
            trigger.on(async ({ text }) => {
                const range = new Range();
                range.selectNode(text);

                const clearedRange = removeFormats(range, base, remove, reformat);
                selection.removeAllRanges();
                selection.addRange(clearedRange);
                selection.collapseToEnd();
            }),
        );
    }

    #formats: Map<string, SurroundFormat<T>> = new Map();

    /**
     * Register a surround format under a certain name.
     * This name is then used with the surround functions to actually apply or
     * remove the given format
     */
    registerFormat(key: string, format: SurroundFormat<T>): () => void {
        this.#formats.set(key, format);

        if (this.#api) {
            this.#triggers.set(
                key,
                this.#api.inputHandler.insertText.trigger({ once: true }),
            );
        }

        return () => this.#formats.delete(key);
    }

    /**
     * Check if a surround format under the given key is registered.
     */
    hasFormat(key: string): boolean {
        return this.#formats.has(key);
    }

    /**
     * Use the surround command on the current range of the input.
     * If the range is already surrounded, it will unsurround instead.
     */
    async surround(formatName: string, exclusiveNames: string[] = []): Promise<void> {
        const base = await this.#assert_base();
        const selection = getSelection(base)!;
        const range = getRange(selection);
        const format = this.#formats.get(formatName);
        const trigger = this.#triggers.get(formatName);

        if (!format || !range || !trigger) {
            return;
        }

        const matcher = boolMatcher(format);

        const exclusives = exclusiveNames
            .map((name) => this.#formats.get(name))
            .filter(isValid);

        if (range.collapsed) {
            return this.#toggleTrigger(
                base,
                selection,
                matcher,
                format,
                trigger,
                exclusives,
            );
        }

        const clearedRange = removeFormats(range, base, exclusives);
        const matches = isSurroundedInner(clearedRange, base, matcher);
        surroundAndSelect(matches, clearedRange, base, format, selection);
    }

    /**
     * Use the surround command on the current range of the input.
     * If the range is already surrounded, it will overwrite the format.
     * This might be better suited if the surrounding is parameterized (like
     * text color).
     */
    async overwriteSurround(
        formatName: string,
        exclusiveNames: string[] = [],
    ): Promise<void> {
        const base = await this.#assert_base();
        const selection = getSelection(base)!;
        const range = getRange(selection);
        const format = this.#formats.get(formatName);
        const trigger = this.#triggers.get(formatName);

        if (!format || !range || !trigger) {
            return;
        }

        const exclusives = exclusiveNames
            .map((name) => this.#formats.get(name))
            .filter(isValid);

        if (range.collapsed) {
            return this.#toggleTriggerOverwrite(
                base,
                selection,
                format,
                trigger,
                exclusives,
            );
        }

        const clearedRange = removeFormats(range, base, exclusives);
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
    async isSurrounded(formatName: string): Promise<boolean> {
        const base = await this.#assert_base();
        const selection = getSelection(base)!;
        const range = getRange(selection);
        const format = this.#formats.get(formatName);
        const trigger = this.#triggers.get(formatName);

        if (!format || !range || !trigger) {
            return false;
        }

        const isSurrounded = isSurroundedInner(range, base, boolMatcher(format));
        return get(trigger.active) ? !isSurrounded : isSurrounded;
    }

    /**
     * Clear/Reformat the provided formats in the current range.
     */
    async remove(formatNames: string[], reformatNames: string[] = []): Promise<void> {
        const base = await this.#assert_base();
        const selection = getSelection(base)!;
        const range = getRange(selection);

        if (!range) {
            return;
        }

        const formats = formatNames
            .map((name) => this.#formats.get(name))
            .filter(isValid);

        const triggers = formatNames
            .map((name) => this.#triggers.get(name))
            .filter(isValid);

        const reformats = reformatNames
            .map((name) => this.#formats.get(name))
            .filter(isValid);

        if (range.collapsed) {
            return this.#toggleTriggerRemove(
                base,
                selection,
                formats,
                triggers,
                reformats,
            );
        }

        const surroundedRange = removeFormats(range, base, formats, reformats);
        selection.removeAllRanges();
        selection.addRange(surroundedRange);
    }
}

registerPackage("anki/surround", {
    Surrounder,
});
