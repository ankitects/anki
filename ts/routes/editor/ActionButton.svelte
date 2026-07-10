<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import LabelButton from "$lib/components/LabelButton.svelte";

    const { children, onClick, tooltip, disabled = false } = $props();
</script>

<div class="action-button">
    <LabelButton
        primary
        on:click={onClick}
        {tooltip}
        --border-left-radius="5px"
        --border-right-radius="5px"
        {disabled}
    >
        <div class="action-text">
            {@render children()}
        </div>
    </LabelButton>
</div>

<style lang="scss">
    .action-button {
        margin-inline-end: 0.5rem;
    }
    .action-text {
        margin: 0 0.75rem;
    }
</style>
