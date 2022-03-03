<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    export let iconSize: number = 100;
    export let widthMultiplier: number = 1;
    export let flipX: boolean = false;

    export let top = 0;
    export let bottom = 0;
    export let left = 0;
    export let right = 0;
</script>

<span
    class="icon-constrain"
    class:flip-x={flipX}
    style:--width-multiplier={widthMultiplier}
    style:--icon-size="{iconSize}%"
    style:top="{top}px"
    style:bottom="{bottom}px"
    style:left="{left}px"
    style:right="{right}px"
    on:click
>
    <slot />
</span>

<style lang="scss">
    span {
        display: inline-block;
        position: relative;
        vertical-align: var(--icon-align, middle);

        /* constrain icon */
        width: calc((var(--buttons-size, 22px) - 2px) * var(--width-multiplier));
        height: calc(var(--buttons-size, 22px) - 2px);

        & > :global(svg),
        & > :global(img) {
            position: absolute;
            width: var(--icon-size);
            height: var(--icon-size);
            top: calc((100% - var(--icon-size)) / 2);
            bottom: calc((100% - var(--icon-size)) / 2);
            left: calc((100% - var(--icon-size)) / 2);
            right: calc((100% - var(--icon-size)) / 2);

            fill: currentColor;
            vertical-align: unset;
        }

        &.flip-x > :global(svg),
        &.flip-x > :global(img) {
            transform: scaleX(-1);
        }
    }
</style>
