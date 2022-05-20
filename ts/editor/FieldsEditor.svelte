<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<div class="fields-editor">
    <div class="fields-editor-content">
        <slot />
    </div>
</div>

<style lang="scss">
    .fields-editor {
        display: flex;
        flex: 0 1 auto;
        overflow-x: auto;
        overflow-y: hidden;
        height: 100%;
    }

    .fields-editor-content {
        width: 100%;
        display: flex;
        flex-direction: column;
    }
</style>
