<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import { createEventDispatcher } from "svelte";
    import Badge from "components/Badge.svelte";
    import TagInput from "./TagInput.svelte";
    import { deleteIcon } from "./icons";

    export let name: string;

    const dispatch = createEventDispatcher();

    let active = false;
    let input: HTMLInputElement;

    function checkForActivation(): void {
        const selection = window.getSelection()!;
        active = selection.isCollapsed;
    }

    function deleteTag(event: Event): void {
        dispatch("tagdelete");
        event.stopPropagation();
    }

    function updateTag(event: Event) {
        active = false;

        if (name.length === 0) {
            deleteTag(event);
        } else {
            dispatch("tagupdate");
            event.stopPropagation();
        }
    }
</script>

{#if active}
    <TagInput
        bind:name
        bind:input
        on:focusout={() => (active = false)}
        on:tagupdate={updateTag}
        on:tagadd
        on:tagjoinprevious
        on:tagjoinnext
        on:mount={(event) => event.detail.input.focus()}
    />
{:else}
    <button
        class="d-inline-flex align-items-center tag text-nowrap rounded ps-2 pe-1 me-1"
        tabindex="-1"
        on:click={checkForActivation}
    >
        <span>{name}</span>
        <Badge class="delete-icon rounded ms-1 mt-1" on:click={deleteTag}
            >{@html deleteIcon}</Badge
        >
    </button>
{/if}

<style lang="scss">
    $white-translucent: rgba(255, 255, 255, 0.5);

    .tag :global(.delete-icon > svg:hover) {
        background-color: $white-translucent;
    }

    button {
        padding: 0;
    }
</style>
