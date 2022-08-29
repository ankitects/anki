<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
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
        overflow-x: hidden;

        /* replace with "gap: 5px" once it's available
           - required: Chromium 84 (Qt6 only) and iOS 14.1 */
        > :global(*) {
            margin: 5px 0;
        }
    }
</style>
