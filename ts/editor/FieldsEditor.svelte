<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<!--
@component
Contains the editor toolbar and the fields.
-->
<div class="fields-editor">
    <slot />
</div>

<style lang="scss">
    .fields-editor {
        position: relative;
        display: flex;
        flex-direction: column;
        flex-grow: 1;

        /* We want to move the scrollbar into the fields and not have it */
        /* extend to the editor toolbar */
        overflow: hidden;
    }
</style>
