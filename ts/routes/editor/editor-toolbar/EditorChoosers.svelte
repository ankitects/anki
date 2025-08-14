<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->

<script lang="ts">
    import NotetypeChooser from "$lib/components/NotetypeChooser.svelte";
    import DeckChooser from "$lib/components/DeckChooser.svelte";
    import type { NotetypeNameId } from "@generated/anki/notetypes_pb";
    import type { DeckNameId } from "@generated/anki/decks_pb";
    import * as tr from "@generated/ftl";

    interface Props {
        selectedNotetype: NotetypeNameId | null;
        selectedDeck: DeckNameId | null;
        onNotetypeChange?: (notetype: NotetypeNameId) => void;
        onDeckChange?: (deck: DeckNameId) => void;
    }
    let {selectedNotetype = $bindable(null), selectedDeck = $bindable(null), onNotetypeChange, onDeckChange}: Props = $props();

</script>

<div class="top-bar">
    <p>{tr.notetypesType()}</p>
    <div class="notetype-chooser">
        <NotetypeChooser bind:selectedNotetype onChange={onNotetypeChange} />
    </div>
    <p>{tr.decksDeck()}</p>
    <div class="deck-chooser">
        <DeckChooser bind:selectedDeck onChange={onDeckChange} />
    </div>
</div>

<style>
    .top-bar {
        display: flex;
        gap: 0.5rem;
        margin-bottom: 0.5rem;
    }

    .notetype-chooser,
    .deck-chooser {
        flex: 1;
    }
</style>
