<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { onMount } from "svelte";

    import {
        getRange,
        getSelection,
        isSelectionCollapsed,
    } from "../../lib/cross-browser";
    import { context } from "../rich-text-input";

    const { inputHandler } = context.get();

    let query: string | null = null;
    let enabled = false;

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
            query = wholeText.substring(offset - 1, offset + 1);
            enabled = true;
        }
    }

    function updateQuery(selection: Selection, text: Text): void {
        const currentRange = getRange(selection)!;
        const offset = currentRange.startOffset;

        if (text.data === ":") {
            text.replaceData(0, text.length, "😊");
            enabled = false;
        } else {
            query += text.data;
        }
    }

    function onInsertText({ event, text }): void {
        const selection = getSelection(event.target)!;

        if (enabled) {
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
    {#if enabled}
        <div>Query: {query}</div>
    {/if}
</div>
