<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { mdiBookOutline } from "./icons";
    import { getDeckNames } from "@generated/backend";
    import ItemChooser from "./ItemChooser.svelte";
    import type { DeckNameId } from "@generated/anki/decks_pb";
    import * as tr from "@generated/ftl";

    interface Props {
        selectedDeck: DeckNameId | null;
        onChange?: (deck: DeckNameId) => void;
    }
    let {selectedDeck = $bindable(null), onChange}: Props = $props();
    let decks: DeckNameId[] = $state([]);

    $effect(() => {
        getDeckNames({ skipEmptyDefault: true, includeFiltered: false }).then(
            (response) => {
                decks = response.entries;
            },
        );
    });
</script>

<ItemChooser
    title={tr.qtMiscChooseDeck()}
    bind:selectedItem={selectedDeck}
    {onChange}
    items={decks}
    icon={mdiBookOutline}
    keyCombination="Control+D"
    tooltip={tr.qtMiscTargetDeckCtrlandd()}
/>
