<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import TagAddButton from "./TagAddButton.svelte";
    import TagsSelectedButton from "./TagsSelectedButton.svelte";

    export let badgeHeight: number;
    export let tagsSelected: boolean;
    export let keyCombination: string;
</script>

<!-- svelte-ignore a11y-no-static-element-interactions -->
<div
    class="tag-options-button gap"
    bind:offsetHeight={badgeHeight}
    on:mousedown|preventDefault
>
    {#key tagsSelected}
        {#if tagsSelected}
            <TagsSelectedButton on:tagselectall on:tagcopy on:tagdelete />
        {:else}
            <TagAddButton {keyCombination} onTagAppend={() => {}} />
        {/if}
    {/key}
</div>

<style lang="scss">
    .tag-options-button {
        transition: opacity 0.2s linear;
        opacity: var(--button-opacity, 1);
    }
</style>
