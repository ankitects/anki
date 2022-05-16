<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import ButtonToolbar from "../../components/ButtonToolbar.svelte";
    import DeckSelector from "./DeckSelector.svelte";
    import NotetypeButtons from "./NotetypeButtons.svelte";
    import NotetypeSelector from "./NotetypeSelector.svelte";

    export let size: number = 1.6;
    export let wrap: boolean = false;
</script>

<ButtonToolbar {size} {wrap}>
    <NotetypeSelector />
    <NotetypeButtons />
    <DeckSelector />
</ButtonToolbar>
