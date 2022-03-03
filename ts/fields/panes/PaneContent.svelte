<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<div class="pane-content">
    <slot />
</div>

<style lang="scss">
    .pane-content {
        display: flex;
        flex-direction: column;
        flex-grow: 1;

        padding: 10px;
        background-color: lightblue;

        overflow: hidden;
        overflow-y: auto;
    }
</style>
