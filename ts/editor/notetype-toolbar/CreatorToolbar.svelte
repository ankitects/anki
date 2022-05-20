<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import ButtonToolbar from "../../components/ButtonToolbar.svelte";
    import type { Notetypes } from "../../lib/proto";
    import AddNoteButton from "./AddNoteButton.svelte";
    import DeckSelector from "./DeckSelector.svelte";
    import FieldsButton from "./FieldsButton.svelte";
    import HistoryButton from "./HistoryButton.svelte";
    import NotetypeSelector from "./NotetypeSelector.svelte";
    import TemplatesButton from "./TemplatesButton.svelte";

    export let notetype: Notetypes.Notetype;
    export let deckId: number;
    export let addedNoteIds: number[];

    export let size: number = 1.6;
    export let wrap: boolean = true;
</script>

<div class="creator-toolbar">
    <ButtonToolbar {size} {wrap}>
        <NotetypeSelector currentNotetypeName={notetype.name} on:notetypechange />
        <FieldsButton />
        <TemplatesButton />
        <DeckSelector currentDeckId={deckId} on:deckchange />

        <div class="creator-toolbar-spacer" />

        <HistoryButton {addedNoteIds} />
        <AddNoteButton on:noteadd />
    </ButtonToolbar>
</div>

<style lang="scss">
    .creator-toolbar {
        margin: 5px;
    }

    .creator-toolbar-spacer {
        flex-grow: 1;
    }
</style>
