<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "../lib/ftl";
    import MapperRow from "./MapperRow.svelte";

    let tagsIndex = 0;
    let deckIndex = 0;
    let notetypeIndex = 0;

    export let columnNames: string[];
    export let tagsColumn: number = tagsIndex - 1;
    export let deckColumn: number = deckIndex - 1;
    export let notetypeColumn: number = notetypeIndex - 1;

    $: tagsColumn = tagsIndex - 1;
    $: deckColumn = deckIndex - 1;
    $: notetypeColumn = notetypeIndex - 1;

    $: options = [tr.changeNotetypeNothing()].concat(columnNames);
</script>

<MapperRow label={tr.editingTags()} {options} bind:index={tagsIndex} />
<MapperRow label={tr.decksDeck()} {options} bind:index={deckIndex} />
<MapperRow label={tr.notetypesNotetype()} {options} bind:index={notetypeIndex} />
