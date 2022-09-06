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
    import { context } from "../rich-text-input";
    import type { SymbolsTable } from "./data-provider";
    import { getSymbolExact, getSymbols } from "./data-provider";

    const SYMBOLS_DELIMITER = ":";

    const { inputHandler, editable } = context.get();

    let referenceRange: Range | null = null;
    let cleanup;
    let query: string | null = null;

    let foundSymbols: SymbolsTable = [];

    function unsetReferenceRange() {
        referenceRange = null;
        cleanup?.();
    }

    async function maybeShowOverlay(
        selection: Selection,
        event: InputEvent,
    ): Promise<void> {
        if (event.inputType !== "insertText") {
            return;
        }

        if (!isSelectionCollapsed(selection)) {
            return;
        }

        const currentRange = getRange(selection)!;
        const offset = currentRange.startOffset;

        if (!(currentRange.commonAncestorContainer instanceof Text) || offset < 2) {
            return;
        }

        const wholeText = currentRange.commonAncestorContainer.wholeText;

        if (wholeText[offset - 2] === SYMBOLS_DELIMITER) {
            referenceRange = currentRange;
            query = wholeText.substring(offset - 1, offset) + event.data;
            foundSymbols = await getSymbols(query);

            cleanup = editable.focusHandler.blur.on(unsetReferenceRange, {
                once: true,
            });
        }
    }

    async function replaceText(
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

    function replaceTextViaMenu(symbolCharacter: string): void {
        const commonAncestor = referenceRange!.commonAncestorContainer as Text;
        const selection = getSelection(commonAncestor)!;

        const replacementLength =
            commonAncestor.data
                .substring(0, referenceRange!.startOffset)
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

    async function updateOverlay(
        selection: Selection,
        event: InputEvent,
    ): Promise<void> {
        if (event.inputType !== "insertText") {
            return unsetReferenceRange();
        }

        const data = event.data;
        referenceRange = getRange(selection)!;

        if (data === SYMBOLS_DELIMITER && query) {
            const symbol = await getSymbolExact(query);

            if (!symbol) {
                return unsetReferenceRange();
            }

            const currentRange = getRange(selection)!;
            const offset = currentRange.startOffset;

            if (!(currentRange.commonAncestorContainer instanceof Text) || offset < 2) {
                return unsetReferenceRange();
            }

            const commonAncestor = currentRange.commonAncestorContainer;

            const replacementLength =
                commonAncestor.data
                    .substring(0, currentRange.startOffset)
                    .split("")
                    .reverse()
                    .join("")
                    .indexOf(SYMBOLS_DELIMITER) + 1;

            commonAncestor.deleteData(
                currentRange.startOffset - replacementLength,
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
            foundSymbols = await getSymbols(query);
        }
    }

    async function onBeforeInput({ event }): Promise<void> {
        const selection = getSelection(event.target)!;

        if (referenceRange) {
            await updateOverlay(selection, event);
        } else {
            await maybeShowOverlay(selection, event);
        }
    }

    onMount(() => inputHandler.beforeInput.on(onBeforeInput));
</script>

<div class="symbols-overlay">
    {#if referenceRange && foundSymbols.length > 0}
        <WithFloating
            reference={referenceRange}
            placement={["top", "bottom"]}
            offset={10}
            on:close={console.log}
        >
            <Popover slot="floating" --popover-padding-inline="0">
                {#each foundSymbols as found (found.name)}
                    <DropdownItem on:click={() => replaceTextViaMenu(found.symbol)}>
                        <div class="symbol-entry">
                            <div class="symbol">{found.symbol}</div>
                            <div class="description">
                                <span class="delimiter">{SYMBOLS_DELIMITER}</span><span
                                    class="name">{found.name}</span
                                ><span class="delimiter">{SYMBOLS_DELIMITER}</span>
                            </div>
                        </div>
                    </DropdownItem>
                {/each}
            </Popover>
        </WithFloating>
    {/if}
</div>

<style lang="scss">
    .symbol-entry {
        display: flex;
        min-width: 140px;
        font-size: 12px;
    }

    .symbol {
        font-size: 150%;
        margin-right: 10px;
    }

    .description {
        align-self: center;
    }
</style>
