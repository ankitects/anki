<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { fly } from "svelte/transition";
</script>

<div class="notification" transition:fly={{ x: 200 }}>
    <slot />
</div>

<style lang="scss">
    .notification {
        background-color: var(--notification-bg, var(--window-bg));
        user-select: none;

        border: 1px solid var(--medium-border);
        border-radius: 5px;
        padding: 0.9rem 1.2rem;
    }
</style>
