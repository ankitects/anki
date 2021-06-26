<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import { createEventDispatcher } from "svelte";
    import Badge from "components/Badge.svelte";
    import { deleteIcon } from "./icons";

    export let name: string;
    export let blink: boolean = false;

    const dispatch = createEventDispatcher();

    $: if (blink) {
        setTimeout(() => (blink = false), 300);
    }

    function deleteTag(event: Event): void {
        dispatch("tagdelete");
        event.stopPropagation();
    }
</script>

<button
    class="d-inline-flex align-items-center tag text-nowrap rounded ps-2 pe-1 me-1"
    class:blink
    tabindex="-1"
    on:click
>
    <span>{name}</span>
    <Badge class="delete-icon rounded ms-1 mt-1" on:click={deleteTag}
        >{@html deleteIcon}</Badge
    >
</button>

<style lang="scss">
    $white-translucent: rgba(255, 255, 255, 0.5);

    @keyframes blink {
        0% {
            filter: brightness(1);
        }
        50% {
            filter: brightness(2);
        }
        100% {
            filter: brightness(1);
        }
    }

    .tag :global(.delete-icon > svg:hover) {
        background-color: $white-translucent;
    }

    button {
        padding: 0;

        &:focus,
        &:active {
            outline: none;
        }

        &.blink {
            animation: blink 0.2s linear;
        }
    }
</style>
