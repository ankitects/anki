<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<span class="field-state justify-content-end">
    <slot />
</span>

<style lang="scss">
    .field-state {
        display: flex;
        justify-content: flex;
        flex-grow: 1;

        /* replace with "gap: 5px" once it's available
           - required: Chromium 84 (Qt6 only) and iOS 14.1 */
        > :global(*) {
            margin: 0 3px;

            &:first-child {
                margin-left: 0;
            }
            &:last-child {
                margin-right: 0;
            }
        }
    }
    :global([dir="rtl"]) .field-state > :global(*) {
        margin: 0 3px;

        &:last-child {
            margin-left: 0;
        }
        &:first-child {
            margin-right: 0;
        }
    }
</style>
