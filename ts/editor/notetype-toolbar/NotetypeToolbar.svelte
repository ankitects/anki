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

    export let notetype: Notetypes.Notetype;

    export let size: number = 1.6;
    export let wrap: boolean = false;
</script>

<div class="notetype-toolbar">
    <ButtonToolbar {size} {wrap}>
        <NotetypeSelector currentNotetypeName={notetype.name} />
        <FieldsButton />
        <CardtypeButton />
        <DeckSelector currentDeckName={"Default"} />
    </ButtonToolbar>
</div>

<style lang="scss">
    .notetype-toolbar {
        margin-bottom: 2px;

        /* Cancel out margins of buttons within */
        margin-left: -4px;
    }
</style>
