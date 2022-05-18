<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import ButtonToolbar from "../../components/ButtonToolbar.svelte";
    import type { Notetypes } from "../../lib/proto";
    import AddNoteButton from "./AddNoteButton.svelte";
    import TemplatesButton from "./TemplatesButton.svelte";
    import DeckSelector from "./DeckSelector.svelte";
    import FieldsButton from "./FieldsButton.svelte";
    import HistoryButton from "./HistoryButton.svelte";
    import NotetypeSelector from "./NotetypeSelector.svelte";

    export let notetype: Notetypes.Notetype;

    export let size: number = 1.6;
    export let wrap: boolean = false;
</script>

<div class="creator-toolbar">
    <ButtonToolbar {size} {wrap}>
        <NotetypeSelector currentNotetypeName={notetype.name} on:notetypechange />
        <FieldsButton />
        <TemplatesButton />
        <DeckSelector currentDeckId={1} on:deckchange />

        <div class="creator-toolbar-spacer" />

        <HistoryButton />
        <AddNoteButton on:noteadd />
    </ButtonToolbar>
</div>

<style lang="scss">
    .creator-toolbar {
        margin-bottom: 4px;

        /* Cancel out margins of buttons within */
        margin-left: -4px;
    }

    .creator-toolbar-spacer {
        flex-grow: 1;
    }
</style>
