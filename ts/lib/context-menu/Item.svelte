<!-- Copyright: Ankitects Pty Ltd and contributors -->
<!-- License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html -->

<script lang="ts">
    const { click, children } = $props();
</script>

<div
    class="context-menu-item"
    onclick={click}
    onkeydown={click}
    role="menuitem"
    tabindex="-1"
>
    {@render children?.()}
</div>

<style lang="scss">
    .context-menu-item {
        padding: 8px 16px;
        cursor: pointer;
        user-select: none;
        white-space: nowrap;

        &:hover {
            background-color: var(--highlight-bg);
        }

        &:active {
            background-color: var(--highlight-bg);
        }
    }
</style>
