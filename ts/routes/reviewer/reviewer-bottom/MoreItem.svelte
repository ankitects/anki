<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    export let shortcut: string = "";
</script>

<div class="row" on:click on:keydown role="button" tabindex="0">
    <slot />
    <span>{shortcut}</span>
</div>

<style lang="scss">
    div.row {
        display: flex;
        align-items: baseline;
        justify-content: space-between;
        padding: 0.5em;
        cursor: pointer;
        line-height: normal;

        &:hover {
            background: var(--highlight-bg);
            color: var(--highlight-fg);
        }

        span {
            min-width: 5em;
            text-align: right;
        }
    }
</style>
