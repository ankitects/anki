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
        grid-gap: 6px;

        padding: 0 3px 5px;
    }
</style>
