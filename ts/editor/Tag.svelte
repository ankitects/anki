<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import { deleteIcon } from "./icons";

    let active = false;

    function activate(): void {
        active = true;
    }
</script>

{#if active}
    <span>active!</span>
{:else}
    <span class="tag text-nowrap bg-secondary rounded p-1 me-2" on:click={activate}>
        <slot />
        {@html deleteIcon}
    </span>
{/if}

<style lang="scss">
    .tag {
        /* important for tags with non-latin characters */
        line-height: 2ch;
        vertical-align: -webkit-baseline-middle;
    }
</style>
