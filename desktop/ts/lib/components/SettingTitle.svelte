<!--
    Copyright: Ankitects Pty Ltd and contributors
    License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<!-- svelte-ignore a11y-no-static-element-interactions -->
<!-- svelte-ignore a11y-click-events-have-key-events -->
<div class="setting-title" on:click>
    <slot />
</div>

<style lang="scss">
    .setting-title {
        cursor: help;
        &:hover {
            text-decoration: underline dotted var(--fg-subtle);
        }
    }
</style>
