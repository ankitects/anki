<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import Warning from "../deck-options/Warning.svelte";
    import { type ImportCsvState } from "./lib";
    import * as tr from "@generated/ftl";

    export let state: ImportCsvState;
    export let maxColumns = 1000;

    const metadata = state.metadata;
    const columnOptions = state.columnOptions;

    let rows: string[][];
    let truncated = false;

    function sanitisePreview(preview: typeof $metadata.preview) {
        let truncated = false;
        const rows = preview.map((x) => {
            if (x.vals.length > maxColumns) {
                truncated = true;
                return x.vals.slice(0, maxColumns);
            }
            return x.vals;
        });
        return { rows, truncated };
    }

    $: ({ rows, truncated } = sanitisePreview($metadata.preview));

    $: warning = truncated ? tr.importingPreviewTruncated({ count: maxColumns }) : "";
</script>

<div class="outer">
    <table class="preview">
        <thead>
            <tr>
                {#each $columnOptions.slice(1) as { label, shortLabel }}
                    <th>
                        {shortLabel || label}
                    </th>
                {/each}
            </tr>
        </thead>
        <tbody>
            {#each rows as row}
                <tr>
                    {#each row as cell}
                        <td>{cell}</td>
                    {/each}
                </tr>
            {/each}
        </tbody>
    </table>
</div>
<Warning {warning} />

<style lang="scss">
    .outer {
        overflow: auto;
        margin-bottom: 0.5rem;
    }

    .preview {
        border-collapse: collapse;
        white-space: nowrap;

        th,
        td {
            text-overflow: ellipsis;
            overflow: hidden;
            border: 1px solid var(--border-subtle);
            padding: 0.25rem 0.5rem;
            max-width: 15em;
        }

        th {
            background: var(--border);
            text-align: center;
        }

        tr {
            &:nth-child(even) {
                background: var(--canvas);
            }
        }

        td {
            text-align: start;
        }
    }
</style>
