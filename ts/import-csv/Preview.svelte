<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import type { Generic } from "../lib/proto";
    import type { ColumnOption } from "./lib";

    export let columnOptions: ColumnOption[];
    export let preview: Generic.StringList[];
</script>

<table id="preview">
    {#each columnOptions.slice(1) as { label }}
        <th>
            {label}
        </th>
    {/each}
    {#each preview as row}
        <tr>
            {#each row.vals as cell}
                <td>{cell}</td>
            {/each}
        </tr>
    {/each}
</table>

<style lang="scss">
    #preview {
        border-collapse: collapse;
        width: 100%;
        white-space: nowrap;

        th,
        td {
            border: 1px solid var(--faint-border);
            padding: 0.25rem 0.5rem;
        }

        th {
            background: var(--medium-border);
            text-align: center;
        }

        tr {
            &:nth-child(even) {
                background: var(--frame-bg);
            }
        }

        td {
            text-align: start;
        }
    }
</style>
