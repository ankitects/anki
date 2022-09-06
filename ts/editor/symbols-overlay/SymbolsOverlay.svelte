<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { onMount } from "svelte";

    import WithFloating from "../../components/WithFloating.svelte";
    import {
        getRange,
        getSelection,
        isSelectionCollapsed,
    } from "../../lib/cross-browser";
    import { context } from "../rich-text-input";
    import { getSymbols } from "./data-provider.ts";

    const { inputHandler } = context.get();
    const symbolsTable = getSymbols();

    let referenceRange: Range | null = null;
    let query: string | null = null;

    function maybeShowOverlay(selection: Selection): void {
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
            query = wholeText.substring(offset - 1, offset + 1);
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

    function updateOverlay(selection: Selection, event: InputEvent): void {
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

            const startOfReplacement = commonAncestor.data
                .substring(0, currentRange.startOffset)
                .split("")
                .reverse()
                .join("")
                .indexOf(":") + 1;

            commonAncestor.deleteData(
                currentRange.startOffset - startOfReplacement,
                startOfReplacement,
            );

            inputHandler.insertText.on(async ({ text }) => replaceText(selection, text), {
                once: true,
            });
        } else if (query) {
            query += data!;
        }
    }

    async function onBeforeInput({ event }): Promise<void> {
        const selection = getSelection(event.target)!;

        if (referenceRange) {
            updateOverlay(selection, event);
        } else {
            maybeShowOverlay(selection);
        }
    }

    onMount(() => inputHandler.beforeInput.on(onBeforeInput));
</script>

<div class="symbols-overlay">
    {#if referenceRange}
        <WithFloating reference={referenceRange} placement={["top", "bottom"]} offset={10}>
            <div slot="floating">Query: {query}</div>
        </WithFloating>
    {/if}
</div>
