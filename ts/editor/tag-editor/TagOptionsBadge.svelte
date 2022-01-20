<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import SelectedTagBadge from "./SelectedTagBadge.svelte";
    import AddTagBadge from "./AddTagBadge.svelte";

    export let badgeHeight: number;
    export let showSelectionsOptions: boolean;
</script>

<div class="gap" bind:offsetHeight={badgeHeight} on:mousedown|preventDefault>
    {#if showSelectionsOptions}
        <SelectedTagBadge
            --badge-align="-webkit-baseline-middle"
            on:tagselectall
            on:tagcopy
            on:tagdelete
        />
    {:else}
        <AddTagBadge --badge-align="-webkit-baseline-middle" on:click />
    {/if}
</div>
