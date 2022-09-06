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
    import { getSymbols } from "./data-provider";

    const { inputHandler } = context.get();

    let referenceRange: Range | null = null;
    let query: string | null = null;

    let foundSymbols: SymbolsTable = [];

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

        if (wholeText[offset - 2] === ":") {
            referenceRange = currentRange;
            query = wholeText.substring(offset - 1, offset) + event.data;
            foundSymbols = await getSymbols(query);
        }
    }

    async function replaceText(selection: Selection, text: Text): Promise<void> {
        text.replaceData(0, text.length, "😊");
        referenceRange = null;

        // Place caret behind it
        const range = new Range();
        range.selectNode(text);

        selection.removeAllRanges();
        selection.addRange(range);
        selection.collapseToEnd();
    }

    async function updateOverlay(
        selection: Selection,
        event: InputEvent,
    ): Promise<void> {
        console.log(event);
        if (event.inputType !== "insertText") {
            referenceRange = null;
            return;
        }

        const data = event.data;
        referenceRange = getRange(selection)!;

        if (data === ":") {
            const currentRange = getRange(selection)!;
            const offset = currentRange.startOffset;

            if (!(currentRange.commonAncestorContainer instanceof Text) || offset < 2) {
                referenceRange = null;
                return;
            }

            const commonAncestor = currentRange.commonAncestorContainer;

            const startOfReplacement =
                commonAncestor.data
                    .substring(0, currentRange.startOffset)
                    .split("")
                    .reverse()
                    .join("")
                    .indexOf(":") + 1;

            commonAncestor.deleteData(
                currentRange.startOffset - startOfReplacement,
                startOfReplacement,
            );

            inputHandler.insertText.on(
                async ({ text }) => replaceText(selection, text),
                {
                    once: true,
                },
            );
        } else if (query) {
            query += data!;
            foundSymbols = await getSymbols(query);
            console.log("query", query);
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
    {#if referenceRange}
        <WithFloating
            reference={referenceRange}
            placement={["top", "bottom"]}
            offset={10}
        >
            <Popover slot="floating">
                {#each foundSymbols as found (found.name)}
                    <DropdownItem>
                        <span>{found.symbol} :{found.name}:</span>
                    </DropdownItem>
                {/each}
            </Popover>
        </WithFloating>
    {/if}
</div>
