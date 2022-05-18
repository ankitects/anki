<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "../../lib/ftl";
    import { addNoteIcon } from "./icons";
    import TextButton from "./TextButton.svelte";
</script>

<div
    class="add-note-button"
    title=""
    on:mousedown|preventDefault
>
    <TextButton>
        <span slot="label">Add note</span>
        <svelte:fragment slot="icon">{@html addNoteIcon}</svelte:fragment>
    </TextButton>
</div>
