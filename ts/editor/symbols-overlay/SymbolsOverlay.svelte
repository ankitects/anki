<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { onMount } from "svelte";

    import DropdownItem from "../../components/DropdownItem.svelte";
    import Popover from "../../components/Popover.svelte";
    import WithFloating from "../../components/WithFloating.svelte";
    import {
        getRange,
        getSelection,
        isSelectionCollapsed,
    } from "../../lib/cross-browser";
    import type { Callback } from "../../lib/typing";
    import { singleCallback } from "../../lib/typing";
    import { context } from "../rich-text-input";
    import type { SymbolsTable } from "./data-provider";
    import { getSymbolExact, getSymbols } from "./data-provider";

    const SYMBOLS_DELIMITER = ":";

    const { inputHandler, editable } = context.get();

    let referenceRange: Range | undefined = undefined;
    let cleanup: Callback;
    let query: string = "";
    let activeItem = 0;

    let foundSymbols: SymbolsTable = [];

    function unsetReferenceRange() {
        referenceRange = undefined;
        activeItem = 0;
        cleanup?.();
    }

    function maybeShowOverlay(selection: Selection, event: InputEvent): void {
        if (
            event.inputType !== "insertText" ||
            event.data === SYMBOLS_DELIMITER ||
            !isSelectionCollapsed(selection)
        ) {
            return unsetReferenceRange();
        }

        const currentRange = getRange(selection)!;
        const offset = currentRange.endOffset;

        if (!(currentRange.commonAncestorContainer instanceof Text) || offset < 2) {
            return unsetReferenceRange();
        }

        const wholeText = currentRange.commonAncestorContainer.wholeText;

        for (let index = offset - 1; index >= 0; index--) {
            const currentCharacter = wholeText[index];

            if (currentCharacter === " ") {
                return unsetReferenceRange();
            } else if (currentCharacter === SYMBOLS_DELIMITER) {
                const possibleQuery =
                    wholeText.substring(index + 1, offset) + event.data;

                if (possibleQuery.length < 2) {
                    return unsetReferenceRange();
                }

                query = possibleQuery;
                referenceRange = currentRange;
                foundSymbols = getSymbols(query);

                cleanup = editable.focusHandler.blur.on(
                    async () => unsetReferenceRange(),
                    {
                        once: true,
                    },
                );
                return;
            }
        }
    }

    function replaceText(
        selection: Selection,
        text: Text,
        symbolCharacter: string,
    ): Promise<void> {
        text.replaceData(0, text.length, symbolCharacter);
        unsetReferenceRange();

        // Place caret behind it
        const range = new Range();
        range.selectNode(text);

        selection.removeAllRanges();
        selection.addRange(range);
        selection.collapseToEnd();
    }

    function replaceTextOnDemand(symbolCharacter: string): void {
        const commonAncestor = referenceRange!.commonAncestorContainer as Text;
        const selection = getSelection(commonAncestor)!;

        const replacementLength =
            commonAncestor.data
                .substring(0, referenceRange!.endOffset)
                .split("")
                .reverse()
                .join("")
                .indexOf(SYMBOLS_DELIMITER) + 1;

        const newOffset = referenceRange!.endOffset - replacementLength + 1;

        commonAncestor.replaceData(
            referenceRange!.endOffset - replacementLength,
            replacementLength + 1,
            symbolCharacter,
        );

        // Place caret behind it
        const range = new Range();
        range.setEnd(commonAncestor, newOffset);
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

        if (data === SYMBOLS_DELIMITER && query) {
            const symbol = getSymbolExact(query);

            if (!symbol) {
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
                async ({ text }) => replaceText(selection, text, symbol),
                {
                    once: true,
                },
            );
        } else if (query) {
            query += data!;
            foundSymbols = getSymbols(query);
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
            replaceTextOnDemand(foundSymbols[activeItem].symbol);
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
                            on:click={() => replaceTextOnDemand(found.symbol)}
                        >
                            <div class="symbol">{found.symbol}</div>
                            <div class="description">
                                {#each found.names as name}
                                    <span class="name">
                                        {SYMBOLS_DELIMITER}{name}{SYMBOLS_DELIMITER}
                                    </span>
                                {/each}
                            </div>
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

    .symbol {
        transform: scale(1.1);
        font-size: 150%;
        /* The widest emojis I could find were couple_with_heart_ */
        width: 38px;
    }

    .description {
        align-self: center;
    }

    .name {
        margin-left: 3px;
    }
</style>
