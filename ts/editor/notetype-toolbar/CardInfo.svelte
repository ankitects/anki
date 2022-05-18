<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { cardIcon } from "./icons";
    import TextButton from "./TextButton.svelte";
    import type { Cards, Notetypes } from "../../lib/proto";

    export let card: Cards.Card;
    export let template: Notetypes.Notetype.Template;
</script>

<div
    class="card-info"
    on:mousedown|preventDefault
>
    <TextButton>
        <span slot="label">{template.name}</span>
        <svelte:fragment slot="icon">{@html cardIcon}</svelte:fragment>
    </TextButton>
</div>
