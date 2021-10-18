<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<div class="fields">
    <slot />
</div>

<style lang="scss">
    .fields {
        display: grid;
        grid-auto-rows: min-content;
        grid-gap: 4px;

        padding: 5px 3px 0;
        /* set height to 100% for rich text widgets */
        height: 100%;

        /* moves the scrollbar inside the editor */
        overflow-x: hidden;

        > :global(:last-child) {
            /* bottom padding is eaten by overflow-x */
            margin-bottom: 5px;
        }
    }
</style>
