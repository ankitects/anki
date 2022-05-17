<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "../lib/ftl";
    import { getNotetypeFields } from "./lib";
    import MapperRow from "./MapperRow.svelte";

    export let columnNames: string[];
    export let notetypeId: number;
    export let fieldColumnIndices: number[];

    $: options = [tr.changeNotetypeNothing()].concat(columnNames);

    let fieldNames: string[] = [];
    let otherFieldIndices: number[] = [];

    function onNotetypeChange(notetypeId: number, columnNames: string[]): void {
        getNotetypeFields(notetypeId).then((newFieldNames) => {
            fieldNames = newFieldNames;
            otherFieldIndices = Array(Math.max(0, fieldNames.length - 1))
                .fill(0)
                .map((_, i) => (i + 1 < columnNames.length ? i + 2 : 0));
        });
    }

    $: onNotetypeChange(notetypeId, columnNames);

    // first index is treated specially, because it must be assigned some column
    let firstFieldIndex: number = 0;
    // other indices are shifted by -1, because the first option is n/a, which
    // is represented by a negative value
    $: fieldColumnIndices = [firstFieldIndex, ...otherFieldIndices.map((i) => i - 1)];
</script>

{#each fieldNames as label, idx}
    {#if idx === 0}
        <MapperRow {label} options={columnNames} bind:index={firstFieldIndex} />
    {:else}
        <MapperRow {label} {options} bind:index={otherFieldIndices[idx - 1]} />
    {/if}
{/each}
