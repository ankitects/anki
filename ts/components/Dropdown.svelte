<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<!-- Alternative to DropdownMenu that avoid Bootstrap -->

<div class="dropdown" on:mousedown|preventDefault>
    <slot />
</div>

<style lang="scss">
    .dropdown {
        border-radius: 5px;
        background-color: var(--frame-bg);
        min-width: 1rem;

        padding: 0.5rem 0;
        font-size: 1rem;
        color: var(--text-fg);

        /* outer border */
        border: 1px solid #060606;
        /* inner border */
        box-shadow: inset 0 0 0 1px #555656;
    }
</style>
