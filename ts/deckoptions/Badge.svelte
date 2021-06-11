<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<span class="badge">
    <slot />
</span>

<style>
    .badge {
        color: inherit;
    }

    span :global(svg) {
        vertical-align: -0.125rem;
        opacity: 0.3;
    }
</style>
