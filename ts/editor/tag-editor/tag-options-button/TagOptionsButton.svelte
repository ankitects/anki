<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import TagAddButton from "./TagAddButton.svelte";
    import TagsSelectedButton from "./TagsSelectedButton.svelte";

    export let badgeHeight: number;
    export let tagsSelected: boolean;
</script>

<div
    class="tag-options-button gap"
    bind:offsetHeight={badgeHeight}
    on:mousedown|preventDefault
>
    {#if tagsSelected}
        <TagsSelectedButton on:tagselectall on:tagcopy on:tagdelete />
    {:else}
        <TagAddButton on:tagappend />
    {/if}
</div>

<style lang="scss">
    .tag-options-button {
        padding: 3px;
    }
</style>
