<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import Badge from "components/Badge.svelte";
    import TagInputEdit from "./TagInputEdit.svelte";
    import { deleteIcon } from "./icons";

    export let name: string;

    let active = false;
</script>

{#if active}
    <TagInputEdit bind:name on:focusout={() => (active = false)} />
{:else}
    <span
        class="tag text-nowrap bg-secondary rounded p-1 me-2"
        on:click|stopPropagation={() => (active = true)}
    >
        <span>{name}</span>
        <Badge class="delete-icon">{@html deleteIcon}</Badge>
    </span>
{/if}

<style lang="scss">
    .tag {
        /* important for tags with non-latin characters */
        line-height: 2ch;
        vertical-align: -webkit-baseline-middle;

        :global(.delete-icon):hover {
            $white-translucent: rgba(255, 255, 255, 0.5);
            background-color: $white-translucent;
        }
    }
</style>
