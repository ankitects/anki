<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "@generated/ftl";

    import Icon from "$lib/components/Icon.svelte";
    import IconButton from "$lib/components/IconButton.svelte";
    import { magnifyIcon } from "$lib/components/icons";
    import VirtualTable from "$lib/components/VirtualTable.svelte";

    import { getRows, showInBrowser } from "./lib";
    import TableCellWithTooltip from "./TableCellWithTooltip.svelte";
    import type { SummarizedLogQueues } from "./types";

    export let summaries: SummarizedLogQueues[];
    export let bottomOffset: number = 0;

    let bottom: HTMLElement;
    $: rows = getRows(summaries);
</script>

<div bind:this={bottom}>
    {#if bottom}
        <VirtualTable
            class="details-table"
            itemHeight={40}
            itemsCount={rows.length}
            {bottomOffset}
        >
            <tr slot="headers">
                <th>#</th>
                <th>{tr.importingStatus()}</th>
                <th>{tr.editingFields()}</th>
                <th></th>
            </tr>
            <svelte:fragment slot="row" let:index>
                <tr>
                    <td class="index-cell">{index + 1}</td>
                    <TableCellWithTooltip
                        class="status-cell"
                        tooltip={rows[index].queue.reason}
                    >
                        {rows[index].summary.action}
                    </TableCellWithTooltip>
                    <TableCellWithTooltip
                        class="contents-cell"
                        tooltip={rows[index].note.fields.join(",")}
                    >
                        {rows[index].note.fields.join(",")}
                    </TableCellWithTooltip>
                    <td class="search-cell">
                        <IconButton
                            class="search-icon"
                            iconSize={100}
                            active={false}
                            disabled={!rows[index].summary.canBrowse}
                            on:click={() => {
                                showInBrowser([rows[index].note]);
                            }}
                        >
                            <Icon icon={magnifyIcon} />
                        </IconButton>
                    </td>
                </tr>
            </svelte:fragment>
        </VirtualTable>
    {/if}
</div>

<style lang="scss">
    :global(.details-table) {
        margin: 0 auto;
        width: 100%;

        :global(.search-icon) {
            border: none !important;
            background: transparent !important;
        }
        tr {
            height: 40px;
            text-align: center;
        }
        .index-cell {
            width: 3em;
        }
        :global(.status-cell) {
            width: 5em;
        }
        :global(.contents-cell) {
            text-align: left;
        }
        :global(.search-cell) {
            width: 3em;
        }
    }
</style>
