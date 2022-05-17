<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import ButtonToolbar from "../../components/ButtonToolbar.svelte";
    import CardtypeButton from "./CardtypeButton.svelte";
    import DeckSelector from "./DeckSelector.svelte";
    import FieldsButton from "./FieldsButton.svelte";
    import NotetypeSelector from "./NotetypeSelector.svelte";

    export let size: number = 1.6;
    export let wrap: boolean = false;
</script>

<ButtonToolbar {size} {wrap}>
    <NotetypeSelector />
    <FieldsButton />
    <CardtypeButton />
    <DeckSelector />
</ButtonToolbar>
