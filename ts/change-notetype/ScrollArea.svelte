<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    export let offset: number;
</script>

<div style="--offset: {offset}px">
    <slot />
</div>

<style>
    div {
        padding: 0, 0.5rem;
        overflow: hidden auto;
        background: var(--pane-bg);
        height: calc(100% - var(--offset));
        border: 1px solid var(--medium-border);
        box-shadow: 0 0 0 1px var(--faint-border);
        border-radius: 0.25rem;
    }
</style>
