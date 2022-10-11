<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    export let iconSize = 100;
    export let widthMultiplier = 1;
    export let flipX = false;
</script>

<span
    class:flip-x={flipX}
    style:--width-multiplier={widthMultiplier}
    style:--icon-size="{iconSize}%"
>
    <slot />
</span>

<style lang="scss">
    span {
        display: inline-block;
        position: relative;
        vertical-align: var(--icon-align, middle);

        /* constrain icon */
        & > :global(svg) {
            position: absolute;
            width: var(--icon-size);
            height: var(--icon-size);

            fill: currentColor;
            vertical-align: unset;
        }

        & > :global(svg:first-of-type) {
            position: static;
        }

        &.flip-x > :global(svg),
        &.flip-x > :global(img) {
            transform: scaleX(-1);
        }
    }
</style>
