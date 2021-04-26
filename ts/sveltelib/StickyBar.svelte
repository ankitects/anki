<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<style lang="scss">
    nav {
        position: sticky;
        top: 0;
        left: 0;
        z-index: 10;

        background: var(--window-bg);
        border-bottom: 1px solid var(--border);
    }
</style>

<nav>
    <slot />
</nav>
