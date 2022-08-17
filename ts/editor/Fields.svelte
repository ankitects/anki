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

        padding: 0 3px;
        /* set height to 100% for rich text widgets */
        height: 100%;

        /* moves the scrollbar inside the editor */
        overflow-x: hidden;

        /* replace with "grid-gap: 6px" as soon as it's available */
        :global(.editor-field) {
            margin: 3px 0;
        }

        > :global(:last-child) {
            /* bottom padding is eaten by overflow-x */
            margin-bottom: 5px;
        }
    }
</style>
