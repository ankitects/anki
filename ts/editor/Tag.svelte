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

    function checkForActivation(): void {
        const selection = window.getSelection()!;
        if (selection.isCollapsed) {
            active = true;
        }
    }

    function tagDelete(event: Event): void {
        dispatch("tagdelete", { name });
        event.stopPropagation();
    }
</script>

{#if active}
    <TagInputEdit bind:name on:focusout={() => (active = false)} />
{:else}
    <span
        class="d-inline-flex align-items-center tag text-nowrap bg-secondary rounded ps-2 pe-1 me-1"
        on:click|stopPropagation={checkForActivation}
    >
        <span>{name}</span>
        <Badge class="delete-icon rounded ms-1 mt-1" on:click={tagDelete}
            >{@html deleteIcon}</Badge
        >
    </span>
{/if}

<style lang="scss">
    $white-translucent: rgba(255, 255, 255, 0.5);

    .tag :global(.delete-icon > svg:hover) {
        background-color: $white-translucent;
    }
</style>
