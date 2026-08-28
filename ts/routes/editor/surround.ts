// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { getRange, getSelection } from "@tslib/cross-browser";
import { asyncNoop } from "@tslib/functional";
import { registerPackage } from "@tslib/runtime-require";
import type { Readable } from "svelte/store";
import { derived, get } from "svelte/store";

import type { Matcher } from "$lib/domlib/find-above";
import { findClosest } from "$lib/domlib/find-above";
import type { SurroundFormat } from "$lib/domlib/surround";
import { boolMatcher, reformat, surround, unsurround } from "$lib/domlib/surround";
import type { TriggerItem } from "$lib/sveltelib/handler-list";
import type { InputHandlerAPI } from "$lib/sveltelib/input-handler";

function isValid<T>(value: T | undefined): value is T {
    return Boolean(value);
}

function isSurroundedInner(
    range: AbstractRange,
    base: HTMLElement,
    matcher: Matcher,
): boolean {
    return Boolean(
        findClosest(range.startContainer, base, matcher)
            || findClosest(range.endContainer, base, matcher),
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

/**
 * After calling disable, using any of the surrounding methods will throw an
 * exception. Make sure to set the input before trying to use them again.
 */
export class Surrounder<T = unknown> {
    #api?: SurroundedAPI;

    #triggers: Map<string, TriggerItem<{ event: InputEvent; text: Text }>> = new Map();
    #formats: Map<string, SurroundFormat<T>> = new Map();

    active: Readable<boolean>;

    private constructor(apiStore: Readable<SurroundedAPI | null>) {
        this.active = derived(apiStore, (api) => Boolean(api));

        apiStore.subscribe((api: SurroundedAPI | null): void => {
            if (api) {
                this.#api = api;

                for (const key of this.#formats.keys()) {
                    this.#triggers.set(
                        key,
                        api.inputHandler.insertText.trigger({ once: true }),
                    );
                }
            } else {
                this.#api = undefined;

                for (const [key, trigger] of this.#triggers) {
                    trigger.off();
                    this.#triggers.delete(key);
                }
            }
        });
    }

    static make<T>(apiStore: Readable<SurroundedAPI | null>): Surrounder<T> {
        return new Surrounder(apiStore);
    }

    #getBaseElement(): Promise<HTMLElement> {
        if (!this.#api) {
            throw new Error("Surrounder: No api set");
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
        formats: {
            format: SurroundFormat<T>;
            trigger: TriggerItem<{ event: InputEvent; text: Text }>;
        }[],
        reformat: SurroundFormat<T>[] = [],
    ): void {
        const remainingFormats = formats
            .filter(({ trigger }) => {
                if (get(trigger.active)) {
                    // Deactivate active triggers for active formats.
                    trigger.off();
                    return false;
                }

                // Otherwise you are within the format. This is why we activate
                // the trigger, so that the active button is set to inactive.
                // We still need to remove the format however.
                trigger.on(asyncNoop);
                return true;
            })
            .map(({ format }) => format);

        // Use an anonymous insertText handler instead of some trigger associated with a name
        this.#api!.inputHandler.insertText.on(
            async ({ text }) => {
                const range = new Range();
                range.selectNode(text);

                const clearedRange = removeFormats(
                    range,
                    base,
                    remainingFormats,
                    reformat,
                );
                selection.removeAllRanges();
                selection.addRange(clearedRange);
                selection.collapseToEnd();
            },
            { once: true },
        );
    }

    /**
     * Check if a surround format under the given key is registered.
     */
    hasFormat(key: string): boolean {
        return this.#formats.has(key);
    }

    /**
     * Register a surround format under a certain key.
     * This name is then used with the surround functions to actually apply or
     * remove the given format.
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
     * Update a surround format under a specific key.
     */
    updateFormat(
        key: string,
        update: (format: SurroundFormat<T>) => SurroundFormat<T>,
    ): void {
        this.#formats.set(key, update(this.#formats.get(key)!));
    }

    /**
     * Use the surround command on the current range of the input.
     * If the range is already surrounded, it will unsurround instead.
     */
    async surround(formatName: string, exclusiveNames: string[] = []): Promise<void> {
        const base = await this.#getBaseElement();
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
        const base = await this.#getBaseElement();
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
        const base = await this.#getBaseElement();
        const selection = getSelection(base)!;
        const range = getRange(selection);
        const format = this.#formats.get(formatName);
        const trigger = this.#triggers.get(formatName);

        if (!range || !format || !trigger) {
            return false;
        }

        const isSurrounded = isSurroundedInner(range, base, boolMatcher(format));
        return get(trigger.active) ? !isSurrounded : isSurrounded;
    }

    /**
     * Clear/Reformat the provided formats in the current range.
     */
    async remove(formatNames: string[], reformatNames: string[] = []): Promise<void> {
        const base = await this.#getBaseElement();
        const selection = getSelection(base)!;
        const range = getRange(selection);

        if (!range) {
            return;
        }

        const activeFormats = formatNames
            .map((name: string) => ({
                name,
                format: this.#formats.get(name)!,
                trigger: this.#triggers.get(name)!,
            }))
            .filter(({ format, trigger }): boolean => {
                if (!format || !trigger) {
                    return false;
                }

                // This is confusing: when nothing is selected, we only
                // include currently-active buttons, as otherwise inactive
                // buttons get toggled on. But when something is selected,
                // we include everything, since we want to remove formatting
                // that may be in part of the selection, but not at the start/end.

                const isSurrounded = !range.collapsed || isSurroundedInner(
                    range,
                    base,
                    boolMatcher(format),
                );
                return get(trigger.active) ? !isSurrounded : isSurrounded;
            });

        const reformats = reformatNames
            .map((name) => this.#formats.get(name))
            .filter(isValid);

        if (range.collapsed) {
            return this.#toggleTriggerRemove(base, selection, activeFormats, reformats);
        }

        const surroundedRange = removeFormats(
            range,
            base,
            activeFormats.map(({ format }) => format),
            reformats,
        );
        selection.removeAllRanges();
        selection.addRange(surroundedRange);
    }
}

registerPackage("anki/surround", {
    Surrounder,
});
