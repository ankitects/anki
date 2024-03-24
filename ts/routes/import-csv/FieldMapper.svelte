<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "@generated/ftl";

    import TitledContainer from "$lib/components/TitledContainer.svelte";

    import type { ImportCsvState } from "./lib";
    import MapperRow from "./MapperRow.svelte";

    export let state: ImportCsvState;

    const metadata = state.metadata;
    const globalNotetype = state.globalNotetype;
    const fieldNamesPromise = state.fieldNames;
    const columnOptions = state.columnOptions;
</script>

<TitledContainer title={tr.importingFieldMapping()}>
    {#if $globalNotetype !== null}
        {#await $fieldNamesPromise then fieldNames}
            {#each fieldNames as label, idx}
                <!-- first index is treated specially, because it must be assigned some column -->
                <MapperRow
                    {label}
                    columnOptions={idx === 0 ? $columnOptions.slice(1) : $columnOptions}
                    bind:value={$globalNotetype.fieldColumns[idx]}
                />
            {/each}
        {/await}
    {/if}
    <MapperRow
        label={tr.editingTags()}
        columnOptions={$columnOptions}
        bind:value={$metadata.tagsColumn}
    />
</TitledContainer>
