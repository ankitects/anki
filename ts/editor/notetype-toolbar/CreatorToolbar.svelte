<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import ButtonToolbar from "../../components/ButtonToolbar.svelte";
    import type { Notetypes } from "../../lib/proto";
    import CardtypeButton from "./CardtypeButton.svelte";
    import DeckSelector from "./DeckSelector.svelte";
    import FieldsButton from "./FieldsButton.svelte";
    import NotetypeSelector from "./NotetypeSelector.svelte";
    import HistoryButton from "./HistoryButton.svelte";
    import AddNoteButton from "./AddNoteButton.svelte";

    export let notetype: Notetypes.Notetype;

    export let size: number = 1.6;
    export let wrap: boolean = false;
</script>

<div class="creator-toolbar">
    <ButtonToolbar {size} {wrap}>
        <NotetypeSelector currentNotetypeName={notetype.name} on:notetypechange />
        <FieldsButton />
        <CardtypeButton />

        <DeckSelector currentDeckId={1} on:deckchange />

        <HistoryButton />
        <AddNoteButton />
    </ButtonToolbar>
</div>

<style lang="scss">
    .creator-toolbar {
        margin-bottom: 4px;

        /* Cancel out margins of buttons within */
        margin-left: -4px;
    }
</style>
