<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import ButtonToolbar from "../../components/ButtonToolbar.svelte";
    import type { Cards, Notetypes } from "../../lib/proto";
    import CardInfo from "./CardInfo.svelte";
    import DeckSelector from "./DeckSelector.svelte";
    import FieldsButton from "./FieldsButton.svelte";
    import NotetypeSelector from "./NotetypeSelector.svelte";
    import TemplatesButton from "./TemplatesButton.svelte";

    export let notetype: Notetypes.Notetype;

    export let card: Cards.Card | undefined = undefined;

    export let size: number = 1.6;
    export let wrap: boolean = false;
</script>

<div class="notetype-toolbar">
    <ButtonToolbar {size} {wrap}>
        <NotetypeSelector currentNotetypeName={notetype.name} on:notetypechange />
        <FieldsButton />
        <TemplatesButton />

        {#if card}
            <DeckSelector currentDeckId={card.deckId} on:deckchange />
            <CardInfo {card} template={notetype.templates[card.templateIdx]} />
        {/if}
    </ButtonToolbar>
</div>

<style lang="scss">
    .notetype-toolbar {
        margin-bottom: 4px;

        /* Cancel out margins of buttons within */
        margin-left: -4px;
    }
</style>
