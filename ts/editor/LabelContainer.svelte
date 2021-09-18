<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<div class="label-container d-flex justify-content-between">
    <slot />
</div>

<style lang="scss">
    .label-container {
        border-width: 0 0 1px;
        border-style: dashed;
        border-color: var(--border-color);
        border-radius: 5px 5px 0 0;

        padding: 0px 6px;
    }
</style>
