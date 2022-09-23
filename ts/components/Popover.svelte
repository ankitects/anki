<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { slide } from "svelte/transition";
    import { pageTheme } from "../sveltelib/theme";

    export let scrollable = false;
</script>

<div
    class="popover"
    class:scrollable
    class:dark={$pageTheme.isDark}
    transition:slide={{ duration: 200 }}
>
    <slot />
</div>

<style lang="scss">
    @use "sass/vars";
    .popover {
        border-radius: 5px;
        background-color: var(--canvas-elevated);
        min-width: var(--popover-width, 1rem);
        max-width: 95vw;

        /* Needs this much space for FloatingArrow to be positioned */
        padding: var(--popover-padding-block, 6px) var(--popover-padding-inline, 6px);

        font-size: 1rem;
        color: var(--fg);

        /* outer border */
        border: 1px solid vars.palette(lightgray, 6);

        &.dark {
            border-color: vars.palette(darkgray, 9);
        }

        /* inner border */
        box-shadow: inset 0 0 0 1px vars.palette(lightgray, 3);

        &.dark {
            box-shadow: inset 0 0 0 1px vars.palette(darkgray, 2);
        }
        &.scrollable {
            max-height: 80vh;
            overflow: hidden auto;
        }
    }
</style>
