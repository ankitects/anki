<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<!--
@component
Contains the fields. This contains the scrollable area.
-->
<div class="fields">
    <slot />
</div>

<style lang="scss">
    .fields {
        display: grid;
        grid-auto-rows: min-content;
        grid-gap: 6px;

        /* Add space after the last field and the start of the tag editor */
        padding-bottom: 5px;

        /* If the fields are to big for the window, this element should have a scrollbar */
        overflow-y: auto;
    }
</style>
