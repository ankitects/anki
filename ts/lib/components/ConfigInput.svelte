<!--
    Copyright: Ankitects Pty Ltd and contributors
    License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    const rtl: boolean = window.getComputedStyle(document.body).direction == "rtl";

    export let grow = true;
</script>

<div
    class="config-input position-relative justify-content-end"
    class:flex-grow-1={grow}
>
    <div class="revert" class:rtl>
        <slot name="revert" />
    </div>
    <slot />
</div>

<style lang="scss">
    .revert {
        position: absolute;
        right: -1.7em;
        bottom: -1px;
        color: var(--fg-faint);
        &.rtl {
            right: unset;
            left: -1.7em;
        }
    }
    .config-input {
        &:hover,
        &:focus-within {
            .revert {
                color: var(--fg-subtle);
            }
        }
        .revert:hover {
            color: var(--fg);
        }
    }
</style>
