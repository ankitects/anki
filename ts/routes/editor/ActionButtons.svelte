<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import AddButton from "./AddButton.svelte";
    import CloseButton from "./CloseButton.svelte";
    import HelpButton from "./HelpButton.svelte";
    import type { EditorMode } from "./types";

    export let mode: EditorMode;
    export let onClose: () => void;
    export let onAdd: () => void;
</script>

<div class="action-buttons d-flex flex-row-reverse">
    {#if mode === "add"}
        <HelpButton />
    {/if}
    {#if mode === "add" || mode === "current"}
        <CloseButton {onClose} enableShortcut={mode === "current"} />
    {/if}
    {#if mode === "add"}
        <AddButton {onAdd} />
    {/if}
</div>

<style lang="scss">
    .action-buttons {
        margin-top: 10px;
    }
</style>
