<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { mdiBookOutline } from "./icons";
    import { getDeck, getDeckNames } from "@generated/backend";
    import ItemChooser from "./ItemChooser.svelte";
    import type { DeckNameId } from "@generated/anki/decks_pb";
    import * as tr from "@generated/ftl";
    import { registerOperationHandler } from "@tslib/operations";
    import { onMount } from "svelte";

    interface Props {
        onChange?: (deck: DeckNameId) => void;
    }
    let { onChange }: Props = $props();
    let selectedDeck: DeckNameId | null = $state(null);
    let decks: DeckNameId[] = $state([]);
    let itemChooser: ItemChooser<DeckNameId> | null = $state(null);

    async function fetchDecks(skipEmptyDefault: boolean = true) {
        decks = (await getDeckNames({ skipEmptyDefault, includeFiltered: false }))
            .entries;
    }

    export function select(notetypeId: bigint) {
        itemChooser?.select(notetypeId);
    }

    export async function getSelected(): Promise<DeckNameId> {
        await fetchDecks(false);
        try {
            await getDeck({ did: selectedDeck!.id }, { alertOnError: false });
        } catch (error) {
            select(1n);
        }
        return selectedDeck!;
    }

    onMount(() => {
        registerOperationHandler((changes) => {
            if (changes.deck) {
                getSelected();
            }
        });
    });

    $effect(() => {
        fetchDecks();
    });
</script>

<ItemChooser
    bind:this={itemChooser}
    title={tr.qtMiscChooseDeck()}
    bind:selectedItem={selectedDeck}
    {onChange}
    items={decks}
    icon={mdiBookOutline}
    keyCombination="Control+D"
    tooltip={tr.qtMiscTargetDeckCtrlandd()}
/>
