<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import CloseButton from "./CloseButton.svelte";
    import type { EditorMode } from "./types";

    export let mode: EditorMode;
    export let onClose: () => void;
</script>

<div class="action-buttons d-flex flex-row-reverse">
    {#if mode === "add" || mode === "current"}
        <CloseButton {onClose} />
    {/if}
</div>

<style lang="scss">
    .action-buttons {
        margin-top: 10px;
    }
</style>
