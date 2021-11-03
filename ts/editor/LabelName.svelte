<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import LabelDescription from "./LabelDescription.svelte";
    export let description = "";
</script>

<span class="label-name">
    <slot />
    {#if description}
        <LabelDescription {description} />
    {/if}
</span>

<style lang="scss">
    .label-name {
        user-select: none;
    }
</style>
