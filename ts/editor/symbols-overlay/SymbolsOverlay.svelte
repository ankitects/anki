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

    const { inputHandler } = context.get();

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

    function updateQuery(selection: Selection, text: Text): void {
        referenceRange = getRange(selection)!;

        if (text.data === ":") {
            text.replaceData(0, text.length, "😊");
        } else {
            query += text.data;
        }
    }

    async function onInsertText({ event, text }): Promise<void> {
        const selection = getSelection(event.target)!;

        if (referenceRange) {
            updateQuery(selection, text);
        } else {
            maybeShowOverlay(selection);
        }

        const range = new Range();
        range.selectNode(text);

        selection.removeAllRanges();
        selection.addRange(range);
        selection.collapseToEnd();
    }

    onMount(() => inputHandler.insertText.on(onInsertText));
</script>

<div class="symbols-overlay">
    {#if referenceRange}
        <WithFloating reference={referenceRange} placement="auto" keepOnKeyup on:close>
            <div slot="floating">Query: {query}</div>
        </WithFloating>
    {/if}
</div>
