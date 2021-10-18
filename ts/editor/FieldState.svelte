<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<span class="field-state justify-content-between">
    <slot />
</span>

<style lang="scss">
    .field-state {
        display: flex;

        & > :global(*) {
            margin-left: 2px;
        }
    }
</style>
