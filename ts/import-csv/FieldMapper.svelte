<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { getNotetypeFields } from "./lib";
    import MapperRow from "./MapperRow.svelte";

    export let notetypeId: number;
    export let columnOptions: string[];
    export let fieldColumns: number[];

    $: fieldNamesPromise = getNotetypeFields(notetypeId);
</script>

{#await fieldNamesPromise then fieldNames}
    {#each fieldNames as label, idx}
        <!-- first index is treated specially, because it must be assigned some column
other indices are shifted by -1, because the first option is n/a, which
is represented by a negative value -->
        {#if idx === 0}
            <MapperRow
                {label}
                options={columnOptions.slice(1)}
                bind:value={fieldColumns[0]}
            />
        {:else}
            <MapperRow
                {label}
                options={columnOptions}
                baseValue={-1}
                bind:value={fieldColumns[idx]}
            />
        {/if}
    {/each}
{/await}
