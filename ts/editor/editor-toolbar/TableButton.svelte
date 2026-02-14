<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "@generated/ftl";
    import Icon from "$lib/components/Icon.svelte";
    import IconButton from "$lib/components/IconButton.svelte";
    import { tableIcon } from "$lib/components/icons";
    import Popover from "$lib/components/Popover.svelte";
    import WithFloating from "$lib/components/WithFloating.svelte";
    import { execCommand } from "$lib/domlib";

    import { context } from "../NoteEditor.svelte";
    import { editingInputIsRichText } from "../rich-text-input";

    let showFloating = false;
    let rows = 0;
    let cols = 0;

    const { focusedInput } = context.get();
    $: disabled = !$focusedInput || !editingInputIsRichText($focusedInput);

    // Reset grid selection when closing
    $: if (!showFloating) {
        rows = 0;
        cols = 0;
    }

    function createTable(r: number, c: number) {
        let html =
            "<table style='border-collapse: collapse; width: 100%; border: 1px solid currentColor;'>";
        for (let i = 0; i < r; i++) {
            html += "<tr>";
            for (let j = 0; j < c; j++) {
                html +=
                    "<td style='border: 1px solid currentColor; padding: 5px; min-width: 30px;'>&nbsp;</td>";
            }
            html += "</tr>";
        }
        html += "</table><br>";
        execCommand("insertHTML", false, html);
        showFloating = false;
        $focusedInput?.focus();
    }

    function onMouseOver(r: number, c: number) {
        rows = r;
        cols = c;
    }
</script>

<WithFloating
    show={showFloating}
    inline
    on:close={() => (showFloating = false)}
    let:asReference
>
    <span class="table-button" use:asReference>
        <IconButton
            tooltip={tr.editingInsertTable()}
            {disabled}
            on:click={() => (showFloating = !showFloating)}
        >
            <Icon icon={tableIcon} />
        </IconButton>
    </span>

    <Popover slot="floating" --popover-padding-inline="0">
        <div class="table-grid">
            {#each Array(10) as _, r}
                <div class="table-row">
                    {#each Array(10) as _, c}
                        <!-- svelte-ignore a11y-click-events-have-key-events -->
                        <!-- svelte-ignore a11y-no-static-element-interactions -->
                        <!-- svelte-ignore a11y-mouse-events-have-key-events -->
                        <div
                            class="table-cell"
                            class:active={r < rows && c < cols}
                            on:mouseover={() => onMouseOver(r + 1, c + 1)}
                            on:mousedown|preventDefault
                            on:click={() => createTable(r + 1, c + 1)}
                        ></div>
                    {/each}
                </div>
            {/each}
        </div>
        <div class="table-size-label">
            {#if rows > 0 && cols > 0}
                {cols} x {rows}
            {:else}
                {tr.editingInsertTable()}
            {/if}
        </div>
    </Popover>
</WithFloating>

<style lang="scss">
    .table-button {
        line-height: 1;
    }
    .table-grid {
        padding: 10px;
        background: var(--canvas);
    }
    .table-row {
        display: flex;
    }
    .table-cell {
        width: 18px;
        height: 18px;
        border: 1px solid var(--border-subtle);
        margin: 1px;
        background: var(--canvas-elevated);
        cursor: pointer;

        &.active {
            background: var(--fg-link);
            border-color: var(--fg-link);
        }
    }
    .table-size-label {
        text-align: center;
        padding-bottom: 5px;
        font-size: 0.9em;
        color: var(--fg);
    }
</style>
