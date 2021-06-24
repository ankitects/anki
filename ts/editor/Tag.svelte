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
        if (selection.isCollapsed) {
            active = true;
            input.focus();
        }
    }

    function deleteTag(event: Event): void {
        dispatch("tagdelete", { name });
        event.stopPropagation();
    }

    function updateTag() {
        active = false;
    }
</script>

{#if active}
    <TagInput
        bind:name
        bind:input
        on:focusout={() => (active = false)}
        on:tagupdate={updateTag}
        on:mount={(event) => event.detail.input.focus()}
    />
{:else}
    <span
        class="d-inline-flex align-items-center tag text-nowrap bg-secondary rounded ps-2 pe-1 me-1"
        on:click|stopPropagation={checkForActivation}
    >
        <span>{name}</span>
        <Badge class="delete-icon rounded ms-1 mt-1" on:click={deleteTag}
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
