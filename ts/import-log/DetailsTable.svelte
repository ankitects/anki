<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "@tslib/ftl";

    import IconButton from "../components/IconButton.svelte";
    import VirtualTable from "../components/VirtualTable.svelte";
    import TableCellWithTooltip from "./TableCellWithTooltip.svelte";
    import { magnifyIcon } from "./icons";
    import { getRows, showInBrowser } from "./lib";
    import type { SummarizedLogQueues } from "./types";

    export let summaries: SummarizedLogQueues[];

    $: rows = getRows(summaries);
</script>

<details>
    <summary>{tr.importingDetails()}</summary>
    <VirtualTable
        class="details-table"
        itemHeight={50}
        itemsCount={rows.length}
        containerHeight={500}
    >
        <tr slot="headers">
            <th>#</th>
            <th>{tr.importingStatus()}</th>
            <th>{tr.importingContents()}</th>
            <th />
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
                            showInBrowser([rows[index].note.id]);
                        }}
                    >
                        {@html magnifyIcon}
                    </IconButton>
                </td>
            </tr>
        </svelte:fragment>
    </VirtualTable>
</details>

<style lang="scss">
    :global(.details-table) {
        margin: 0 auto;
        width: 100%;

        :global(.search-icon) {
            border: none !important;
            background: transparent !important;
        }
        :global(tr) {
            height: 50px;
            text-align: center;
        }
        :global(.index-cell) {
            width: 6em;
        }
        :global(.status-cell) {
            width: 5em;
        }
        :global(.contents-cell) {
            text-align: left;
        }
        :global(.search-cell) {
            width: 4em;
        }
    }
</style>
