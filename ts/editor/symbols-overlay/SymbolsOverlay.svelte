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
    import {
        getRange,
        getSelection,
        isSelectionCollapsed,
    } from "../../lib/cross-browser";
    import type { Callback } from "../../lib/typing";
    import { singleCallback } from "../../lib/typing";
    import { context } from "../rich-text-input";
    import type {
        SymbolsEntry as SymbolsEntryType,
        SymbolsTable,
    } from "./symbols-table";
    import { findSymbols, getAutoInsertSymbol, getExactSymbol } from "./symbols-table";
    import SymbolsEntry from "./SymbolsEntry.svelte";

    const SYMBOLS_DELIMITER = ":";
    const whitespaceCharacters = [" ", "\u00a0"];

    const { inputHandler, editable } = context.get();
    const fontFamily = getContext<Readable<string>>(fontFamilyKey);

    let referenceRange: Range | undefined = undefined;
    let cleanup: Callback;

    let searchQuery: string = "";
    let activeItem = 0;

    let foundSymbols: SymbolsTable = [];

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
    import { createDummyDoc } from "../../lib/parsing";

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
        if (query.length >= 2) {
            const symbolEntry = getAutoInsertSymbol(query);

            if (symbolEntry) {
                const commonAncestor = range.commonAncestorContainer as Text;
                const replacementLength = query.length;

                commonAncestor.deleteData(
                    range.endOffset - replacementLength + 1,
                    replacementLength,
                );

                inputHandler.insertText.on(
                    async ({ text }) =>
                        replaceText(
                            selection,
                            text,
                            symbolsEntryToReplacement(symbolEntry),
                        ),
                    {
                        once: true,
                    },
                );

                return true;
            }
        }

        return false;
    }

    function maybeShowOverlay(selection: Selection, event: InputEvent): void {
        if (
            event.inputType !== "insertText" ||
            !event.data ||
            event.data === SYMBOLS_DELIMITER ||
            whitespaceCharacters.includes(event.data) ||
            !isSelectionCollapsed(selection)
        ) {
            return unsetReferenceRange();
        }

        const currentRange = getRange(selection)!;
        const offset = currentRange.endOffset;

        if (!(currentRange.commonAncestorContainer instanceof Text)) {
            return unsetReferenceRange();
        }

        const wholeText = currentRange.commonAncestorContainer.wholeText;
        let possibleQuery = event.data;

        for (let index = offset - 1; index >= 0; index--) {
            const currentCharacter = wholeText[index];

            if (whitespaceCharacters.includes(currentCharacter)) {
                return unsetReferenceRange();
            } else if (currentCharacter === SYMBOLS_DELIMITER) {
                if (possibleQuery.length < 2) {
                    return unsetReferenceRange();
                }

                searchQuery = possibleQuery;
                referenceRange = currentRange;
                foundSymbols = findSymbols(searchQuery);

                cleanup = editable.focusHandler.blur.on(
                    async () => unsetReferenceRange(),
                    {
                        once: true,
                    },
                );
                return;
            }

            possibleQuery = currentCharacter + possibleQuery;

            if (tryAutoInsert(selection, currentRange, possibleQuery)) {
                return;
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
                .indexOf(SYMBOLS_DELIMITER) + 1;

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

    function updateOverlay(selection: Selection, event: InputEvent): void {
        if (event.inputType !== "insertText") {
            return unsetReferenceRange();
        }

        const data = event.data;
        referenceRange = getRange(selection)!;

        if (data === SYMBOLS_DELIMITER && searchQuery) {
            const symbolEntry = getExactSymbol(searchQuery);

            if (!symbolEntry) {
                return unsetReferenceRange();
            }

            const currentRange = getRange(selection)!;
            const offset = currentRange.endOffset;

            if (!(currentRange.commonAncestorContainer instanceof Text) || offset < 2) {
                return unsetReferenceRange();
            }

            const commonAncestor = currentRange.commonAncestorContainer;

            const replacementLength =
                commonAncestor.data
                    .substring(0, currentRange.endOffset)
                    .split("")
                    .reverse()
                    .join("")
                    .indexOf(SYMBOLS_DELIMITER) + 1;

            commonAncestor.deleteData(
                currentRange.endOffset - replacementLength,
                replacementLength,
            );

            inputHandler.insertText.on(
                async ({ text }) =>
                    replaceText(
                        selection,
                        text,
                        symbolsEntryToReplacement(symbolEntry),
                    ),
                {
                    once: true,
                },
            );
        } else if (searchQuery) {
            searchQuery += data!;
            foundSymbols = findSymbols(searchQuery);
        }
    }

    function onBeforeInput({ event }): void {
        const selection = getSelection(event.target)!;

        if (referenceRange) {
            updateOverlay(selection, event);
        } else {
            maybeShowOverlay(selection, event);
        }
    }

    $: showSymbolsOverlay = referenceRange && foundSymbols.length > 0;

    function onSpecialKey({ event, action }): void {
        if (!showSymbolsOverlay) {
            return;
        }

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

    onMount(() =>
        singleCallback(
            inputHandler.beforeInput.on(onBeforeInput),
            inputHandler.specialKey.on(onSpecialKey),
            inputHandler.pointerDown.on(async () => unsetReferenceRange()),
        ),
    );
</script>

<div class="symbols-overlay">
    {#if showSymbolsOverlay}
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
                                {SYMBOLS_DELIMITER}{symbolName}{SYMBOLS_DELIMITER}
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
