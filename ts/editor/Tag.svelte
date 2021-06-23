<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import { createEventDispatcher } from "svelte";
    import Badge from "components/Badge.svelte";
    import TagInputEdit from "./TagInputEdit.svelte";
    import { deleteIcon } from "./icons";

    export let name: string;

    const dispatch = createEventDispatcher();

    let active = false;

    function tagDelete(event: Event): void {
        dispatch("tagdelete", { name });
        event.stopPropagation();
    }
</script>

{#if active}
    <TagInputEdit bind:name on:focusout={() => (active = false)} />
{:else}
    <span
        class="tag text-nowrap bg-secondary rounded p-1 ps-2 me-2"
        on:click|stopPropagation={() => (active = true)}
    >
        <span>{name}</span>
        <Badge class="rounded delete-icon" on:click={tagDelete}
            >{@html deleteIcon}</Badge
        >
    </span>
{/if}

<style lang="scss">
    $white-translucent: rgba(255, 255, 255, 0.5);

    .tag :global(.delete-icon) {
        & > :global(svg) {
            vertical-align: text-bottom;
        }

        & > :global(svg:hover) {
            background-color: $white-translucent;
        }
    }
</style>
