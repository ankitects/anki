<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { mdiBookOutline } from "./icons";
    import { getDeckNames } from "@generated/backend";
    import ItemChooser from "./ItemChooser.svelte";
    import type { DeckNameId } from "@generated/anki/decks_pb";

    let decks: DeckNameId[] = $state([]);
    let selectedDeck: DeckNameId | null = $state(null);

    $effect(() => {
        getDeckNames({ skipEmptyDefault: true, includeFiltered: false }).then(
            (response) => {
                decks = response.entries;
            },
        );
    });
</script>

<ItemChooser
    title="Choose Deck"
    searchPlaceholder="Search decks..."
    bind:selectedItem={selectedDeck}
    items={decks}
    icon={mdiBookOutline}
/>
