<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { pageTheme } from "../sveltelib/theme";
</script>

<div class="arrow" class:dark={$pageTheme.isDark} />

<style lang="scss">
    @use "sass/elevation" as elevation;

    .arrow {
        background-color: var(--canvas-elevated);
        width: 10px;
        height: 10px;
        z-index: 60;

        /* outer border */
        border: 1px solid var(--border-subtle);

        /* Rotate the box to indicate the different directions */
        border-right: none;
        border-bottom: none;

        &.dark {
            box-shadow: inset 1px 1px 0 0 #565656;
        }
    }
</style>
