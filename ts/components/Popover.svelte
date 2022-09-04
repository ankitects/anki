<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

Alternative to DropdownMenu that avoids Bootstrap
-->
<script>
    import { pageTheme } from "../sveltelib/theme";
</script>

<div class="popover" class:dark={$pageTheme.isDark} on:mousedown|preventDefault>
    <slot />
</div>

<style lang="scss">
    .popover {
        border-radius: 5px;
        background-color: var(--canvas-elevated);
        min-width: 1rem;
        max-width: 95vw;

        padding: 0.5rem 0;
        font-size: 1rem;
        color: var(--fg-default);

        /* outer border */
        border: 1px solid #b6b6b6;

        &.dark {
            border-color: #060606;
        }

        /* inner border */
        box-shadow: inset 0 0 0 1px #eeeeee;

        &.dark {
            box-shadow: inset 0 0 0 1px #565656;
        }
    }
</style>
