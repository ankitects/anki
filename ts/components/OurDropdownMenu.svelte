<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
</script>

<div class="our-dropdown-menu" on:mousedown|preventDefault>
    <slot />
</div>

<style lang="scss">
    .our-dropdown-menu {
        border-radius: 5px;
        background-color: var(--frame-bg);
        min-width: 1rem;

        padding: 0.5rem 0;
        font-size: 1rem;
        color: var(--text-fg);
    }
</style>
