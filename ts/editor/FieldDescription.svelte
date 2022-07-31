<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<div class="field-description">
    <slot />
</div>

<style>
    .field-description {
        opacity: 0.4;
        cursor: text;
        /* same as in ContentEditable */
        padding: 6px;
        /* stay a on single line */
        position: absolute;
        max-width: 95%;
        overflow-x: hidden;
        white-space: nowrap;
        text-overflow: ellipsis;
    }
</style>
