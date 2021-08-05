<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { createEventDispatcher, getContext } from "svelte";
    import { nightModeKey } from "components/context-keys";

    export let offsetX = 0;
    export let offsetY = 0;

    export let active = false;
    export let activeSize = 5;

    const dispatch = createEventDispatcher();
    const nightMode = getContext(nightModeKey);

    const onPointerdown =
        (north: boolean, west: boolean) =>
        (event: PointerEvent): void => {
            dispatch("pointerclick", { north, west, originalEvent: event });
        };
</script>

<div
    class="d-contents"
    style="--offsetX: {offsetX}px; --offsetY: {offsetY}px; --activeSize: {activeSize}px;"
>
    <div
        class:nightMode
        class:active
        class="nw"
        on:mousedown|preventDefault
        on:pointerdown={onPointerdown(true, true)}
        on:pointerup
        on:pointermove
    />
    <div
        class:nightMode
        class:active
        class="ne"
        on:mousedown|preventDefault
        on:pointerdown={onPointerdown(true, false)}
        on:pointerup
        on:pointermove
    />
    <div
        class:nightMode
        class:active
        class="sw"
        on:mousedown|preventDefault
        on:pointerdown={onPointerdown(false, true)}
        on:pointerup
        on:pointermove
    />
    <div
        class:nightMode
        class:active
        class="se"
        on:mousedown|preventDefault
        on:pointerdown={onPointerdown(false, false)}
        on:pointerup
        on:pointermove
    />
</div>

<style lang="scss">
    .d-contents {
        display: contents;
    }

    div > div {
        position: absolute;

        width: var(--activeSize);
        height: var(--activeSize);
        border: 1px solid black;

        &.active {
            background-color: black;
        }

        &.nightMode {
            border-color: white;

            &.active {
                background-color: white;
            }
        }

        &.nw {
            top: calc(0px - var(--offsetY));
            left: calc(0px - var(--offsetX));
            border-bottom: none;
            border-right: none;

            &.active {
                cursor: nw-resize;
            }
        }

        &.ne {
            top: calc(0px - var(--offsetY));
            right: calc(0px - var(--offsetX));
            border-bottom: none;
            border-left: none;

            &.active {
                cursor: ne-resize;
            }
        }

        &.sw {
            bottom: calc(0px - var(--offsetY));
            left: calc(0px - var(--offsetX));
            border-top: none;
            border-right: none;

            &.active {
                cursor: sw-resize;
            }
        }

        &.se {
            bottom: calc(0px - var(--offsetY));
            right: calc(0px - var(--offsetX));
            border-top: none;
            border-left: none;

            &.active {
                cursor: se-resize;
            }
        }
    }
</style>
