<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "../lib/ftl";
    import MapperRow from "./MapperRow.svelte";

    export let columnNames: string[];
    export let tagsColumn: number = 0;
    export let deckColumn: number = 0;
    export let notetypeColumn: number = 0;

    $: options = [tr.changeNotetypeNothing()].concat(columnNames);
</script>

<MapperRow label={tr.editingTags()} {options} bind:index={tagsColumn} />
<MapperRow label={tr.decksDeck()} {options} bind:index={deckColumn} />
<MapperRow label={tr.notetypesNotetype()} {options} bind:index={notetypeColumn} />
