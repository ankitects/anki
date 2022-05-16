<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import Container from "../components/Container.svelte";
    import Spacer from "../components/Spacer.svelte";
    import * as tr from "../lib/ftl";
    import { getNotetypeFields } from "./lib";
    import MapperRow from "./MapperRow.svelte";

    export let columnNames: string[];
    export let notetypeId: number;
    export let fieldColumnIndices: number[];

    $: options = [tr.changeNotetypeNothing()].concat(columnNames);

    let fieldNames: string[] = [];
    $: {
        getNotetypeFields(notetypeId).then((newFieldNames) => {
            fieldNames = newFieldNames;
        });
    }

    let firstFieldIndex: number = 0;
    $: otherFieldIndices = Array(Math.max(0, fieldNames.length - 1))
        .fill(0)
        .map((_, i) => (i + 1 < columnNames.length ? i + 2 : 0));

    $: fieldColumnIndices = [firstFieldIndex, ...otherFieldIndices.map((i) => i - 1)];
</script>

<Spacer --height="0.5rem" />

<Container --gutter-inline="0.5rem" --gutter-block="0.15rem">
    {#each fieldNames as label, idx}
        {#if idx === 0}
            <MapperRow {label} options={columnNames} bind:index={firstFieldIndex} />
        {:else}
            <MapperRow {label} {options} bind:index={otherFieldIndices[idx - 1]} />
        {/if}
    {/each}
</Container>
