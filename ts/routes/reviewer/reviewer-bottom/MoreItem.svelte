<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    export let shortcut: string = "";
    export let submenu: boolean = false;
</script>

<div class="row" on:click on:keydown role="button" tabindex="0">
    <slot />
    <span>{shortcut}</span>
    {#if submenu}
        <span class="chevron">{"▸"}</span>
    {/if}
</div>

<style lang="scss">
    div.row {
        display: flex;
        align-items: baseline;
        justify-content: space-between;
        padding: 0.5em;
        cursor: pointer;
        line-height: normal;
        gap: 0.5em;

        &:hover {
            background: var(--highlight-bg);
            color: var(--highlight-fg);
        }

        span {
            min-width: 6em;
        }
    }

    .chevron {
        text-align: right;
        padding-right: 0.5em;
        opacity: 50%;
    }
</style>
