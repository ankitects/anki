<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { getContext, onMount } from "svelte";
    import type { Readable } from "svelte/store";

    import DropdownItem from "../../components/DropdownItem.svelte";
    import Popover from "../../components/Popover.svelte";
    import WithFloating from "../../components/WithFloating.svelte";
    import { fontFamilyKey } from "../../lib/context-keys";
    import { getRange, getSelection } from "../../lib/cross-browser";
    import { createDummyDoc } from "../../lib/parsing";
    import type { Callback } from "../../lib/typing";
    import { singleCallback } from "../../lib/typing";
    import type { SpecialKeyParams } from "../../sveltelib/input-handler";
    import { context } from "../rich-text-input";
    import { findSymbols, getAutoInsertSymbol, getExactSymbol } from "./symbols-table";
    import type {
        SymbolsEntry as SymbolsEntryType,
        SymbolsTable,
    } from "./symbols-types";
    import SymbolsEntry from "./SymbolsEntry.svelte";

    const symbolsDelimiter = ":";
    const queryMinLength = 2;
    const autoInsertQueryMaxLength = 5;

    const whitespaceCharacters = [" ", "\u00a0"];

    const { editable, inputHandler } = context.get();
    const fontFamily = getContext<Readable<string>>(fontFamilyKey);

    let foundSymbols: SymbolsTable = [];

    let referenceRange: Range | undefined = undefined;
    let activeItem = 0;
    let cleanup: Callback;

    function unsetReferenceRange() {
        referenceRange = undefined;
        activeItem = 0;
        cleanup?.();
    }

    function replaceText(selection: Selection, text: Text, nodes: Node[]): void {
        text.deleteData(0, text.length);
        text.after(...nodes);

        unsetReferenceRange();

        // Place caret behind it
        const range = new Range();
        range.setEndAfter(nodes[nodes.length - 1]);
        range.collapse(false);

        selection.removeAllRanges();
        selection.addRange(range);
    }

    const parser = new DOMParser();

    function symbolsEntryToReplacement(entry: SymbolsEntryType): Node[] {
        if (entry.containsHTML) {
            const doc = parser.parseFromString(
                createDummyDoc(entry.symbol),
                "text/html",
            );
            return [...doc.body.childNodes];
        } else {
            return [new Text(entry.symbol)];
        }
    }

    function tryAutoInsert(selection: Selection, range: Range, query: string): boolean {
        if (
            query.length >= queryMinLength &&
            query.length <= autoInsertQueryMaxLength
        ) {
            const symbolEntry = getAutoInsertSymbol(query);

            if (symbolEntry) {
                const commonAncestor = range.commonAncestorContainer as Text;
                const replacementLength = query.length;

                commonAncestor.parentElement?.normalize();
                commonAncestor.deleteData(
                    range.endOffset - replacementLength + 1,
                    replacementLength,
                );

                inputHandler.insertText.on(
                    async ({ text }) => {
                        replaceText(
                            selection,
                            text,
                            symbolsEntryToReplacement(symbolEntry),
                        );
                    },
                    {
                        once: true,
                    },
                );

                return true;
            }
        }

        return false;
    }

    function findValidSearchQuery(
        selection: Selection,
        range: Range,
        startQuery = "",
        shouldFinishEarly: (
            selection: Selection,
            range: Range,
            query: string,
        ) => boolean = () => false,
    ): string | null {
        const offset = range.endOffset;
        const commonAncestorContainer = range.commonAncestorContainer;

        if (!(commonAncestorContainer instanceof Text)) {
            return null;
        }

        let query = startQuery;

        for (let index = offset - 1; index >= 0; index--) {
            const currentCharacter = commonAncestorContainer.wholeText[index];

            if (whitespaceCharacters.includes(currentCharacter)) {
                return null;
            } else if (currentCharacter === symbolsDelimiter) {
                if (query.length < queryMinLength) {
                    return null;
                }

                return query;
            }

            query = currentCharacter + query;

            if (shouldFinishEarly(selection, range, query)) {
                return null;
            }
        }

        return null;
    }

    function onSpecialKey({ event, action }): void {
        if (["caretLeft", "caretRight"].includes(action)) {
            return unsetReferenceRange();
        }

        event.preventDefault();

        if (action === "caretUp") {
            if (activeItem === 0) {
                activeItem = foundSymbols.length - 1;
            } else {
                activeItem--;
            }
        } else if (action === "caretDown") {
            if (activeItem >= foundSymbols.length - 1) {
                activeItem = 0;
            } else {
                activeItem++;
            }
        } else if (action === "enter" || action === "tab") {
            replaceTextOnDemand(foundSymbols[activeItem]);
        } else if (action === "escape") {
            unsetReferenceRange();
        }
    }

    function maybeShowOverlay(selection: Selection, event: InputEvent): void {
        if (!event.data) {
            return;
        }

        const currentRange = getRange(selection)!;

        // The input event opening the overlay or triggering the auto-insert
        // must be an insertion, so event.data must be a string.
        // If the inputType is insertCompositionText, the event.data will
        // contain the current composition, but the document will also
        // contain the whole composition except the last character.
        // So we only take the last character from event.data and retrieve the
        // rest from the document
        const startQuery = event.data[event.data.length - 1];
        const query = findValidSearchQuery(
            selection,
            currentRange,
            startQuery,
            tryAutoInsert,
        );

        if (query) {
            foundSymbols = findSymbols(query);

            if (foundSymbols.length > 0) {
                referenceRange = currentRange;
                cleanup = singleCallback(
                    editable.focusHandler.blur.on(async () => unsetReferenceRange(), {
                        once: true,
                    }),
                    inputHandler.pointerDown.on(async () => unsetReferenceRange()),
                    inputHandler.specialKey.on(async (input: SpecialKeyParams) =>
                        onSpecialKey(input),
                    ),
                );
            }
        }
    }

    function replaceTextOnDemand(entry: SymbolsEntryType): void {
        const commonAncestor = referenceRange!.commonAncestorContainer as Text;
        const selection = getSelection(commonAncestor)!;

        const replacementLength =
            commonAncestor.data
                .substring(0, referenceRange!.endOffset)
                .split("")
                .reverse()
                .join("")
                .indexOf(symbolsDelimiter) + 1;

        commonAncestor.deleteData(
            referenceRange!.endOffset - replacementLength,
            replacementLength + 1,
        );

        const nodes = symbolsEntryToReplacement(entry);
        commonAncestor.after(...nodes);

        const range = new Range();
        range.setEndAfter(nodes[nodes.length - 1]);
        range.collapse(false);

        selection.removeAllRanges();
        selection.addRange(range);

        unsetReferenceRange();
    }

    function prepareInsertion(selection: Selection, query: string): void {
        const symbolEntry = getExactSymbol(query);

        if (!symbolEntry) {
            return unsetReferenceRange();
        }

        const currentRange = getRange(selection)!;
        const offset = currentRange.endOffset;

        if (
            !(currentRange.commonAncestorContainer instanceof Text) ||
            offset < queryMinLength
        ) {
            return unsetReferenceRange();
        }

        const commonAncestor = currentRange.commonAncestorContainer;

        const replacementLength =
            commonAncestor.data
                .substring(0, currentRange.endOffset)
                .split("")
                .reverse()
                .join("")
                .indexOf(symbolsDelimiter) + 1;

        commonAncestor.deleteData(
            currentRange.endOffset - replacementLength,
            replacementLength,
        );

        inputHandler.insertText.on(
            async ({ text }) =>
                replaceText(selection, text, symbolsEntryToReplacement(symbolEntry)),
            {
                once: true,
            },
        );
    }

    function updateOverlay(selection: Selection, event: InputEvent): void {
        if (event.data === symbolsDelimiter) {
            const query = findValidSearchQuery(selection, getRange(selection)!);

            if (query) {
                prepareInsertion(selection, query);
            } else {
                unsetReferenceRange();
            }
        }
        // We have to wait for afterInput to update the symbols, because we also
        // want to update in the case of a deletion
        inputHandler.afterInput.on(
            async (): Promise<void> => {
                const currentRange = getRange(selection)!;
                const query = findValidSearchQuery(selection, currentRange);

                if (!query) {
                    return unsetReferenceRange();
                }

                foundSymbols = findSymbols(query);

                if (foundSymbols.length === 0) {
                    unsetReferenceRange();
                } else {
                    referenceRange = currentRange;
                }
            },
            { once: true },
        );
    }

    function onBeforeInput({ event }): void {
        const selection = getSelection(event.target)!;

        if (referenceRange) {
            updateOverlay(selection, event);
        } else {
            maybeShowOverlay(selection, event);
        }
    }

    onMount(() =>
        inputHandler.beforeInput.on(
            async (input: { event: Event }): Promise<void> => onBeforeInput(input),
        ),
    );
</script>

<div class="symbols-overlay">
    {#if referenceRange}
        <WithFloating
            reference={referenceRange}
            placement={["top", "bottom"]}
            offset={10}
        >
            <Popover slot="floating" --popover-padding-inline="0">
                <div class="symbols-menu">
                    {#each foundSymbols as found, index (found.symbol)}
                        <DropdownItem
                            active={index === activeItem}
                            on:click={() => replaceTextOnDemand(found)}
                        >
                            <SymbolsEntry
                                let:symbolName
                                symbol={found.symbol}
                                names={found.names}
                                containsHTML={Boolean(found.containsHTML)}
                                fontFamily={$fontFamily}
                            >
                                {symbolsDelimiter}{symbolName}{symbolsDelimiter}
                            </SymbolsEntry>
                        </DropdownItem>
                    {/each}
                </div>
            </Popover>
        </WithFloating>
    {/if}
</div>

<style lang="scss">
    .symbols-menu {
        display: flex;
        flex-flow: column nowrap;

        min-width: 140px;
        max-height: 15rem;

        font-size: 12px;
        overflow-x: hidden;
        text-overflow: ellipsis;
        overflow-y: auto;
    }
</style>
