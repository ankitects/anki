<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { createEventDispatcher } from "svelte";

    import { pageTheme } from "$lib/sveltelib/theme";

    export let offsetX = 0;
    export let offsetY = 0;

    export let active = false;
    export let activeSize = 5;

    const dispatch = createEventDispatcher();

    const onPointerdown =
        (north: boolean, west: boolean) =>
        (event: PointerEvent): void => {
            dispatch("pointerclick", { north, west, originalEvent: event });
        };
</script>

<div
    class="handle-control"
    style="--offsetX: {offsetX}px; --offsetY: {offsetY}px; --activeSize: {activeSize}px;"
>
    <div
        class:nightMode={$pageTheme.isDark}
        class="bordered"
        on:mousedown|preventDefault
        tabindex="-1"
        role="button"
    ></div>
    <div
        class:nightMode={$pageTheme.isDark}
        class:active
        class="control nw"
        on:mousedown|preventDefault
        on:pointerdown={onPointerdown(true, true)}
        on:pointermove
        tabindex="-1"
        role="button"
    ></div>
    <div
        class:nightMode={$pageTheme.isDark}
        class:active
        class="control ne"
        on:mousedown|preventDefault
        on:pointerdown={onPointerdown(true, false)}
        on:pointermove
        tabindex="-1"
        role="button"
    ></div>
    <div
        class:nightMode={$pageTheme.isDark}
        class:active
        class="control sw"
        on:mousedown|preventDefault
        on:pointerdown={onPointerdown(false, true)}
        on:pointermove
        tabindex="-1"
        role="button"
    ></div>
    <div
        class:nightMode={$pageTheme.isDark}
        class:active
        class="control se"
        on:mousedown|preventDefault
        on:pointerdown={onPointerdown(false, false)}
        on:pointermove
        tabindex="-1"
        role="button"
    ></div>
</div>

<style lang="scss">
    .handle-control {
        display: contents;
    }

    .bordered {
        position: absolute;

        top: calc(0px - var(--activeSize) + var(--offsetY));
        bottom: calc(0px - var(--activeSize) + var(--offsetY));
        left: calc(0px - var(--activeSize) + var(--offsetX));
        right: calc(0px - var(--activeSize) + var(--offsetX));

        pointer-events: none;
        border: 2px dashed black;

        &.nightMode {
            border-color: white;
        }
    }

    .control {
        position: absolute;

        width: var(--activeSize);
        height: var(--activeSize);

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

            &.active {
                cursor: nw-resize;
            }
        }

        &.ne {
            top: calc(0px - var(--offsetY));
            right: calc(0px - var(--offsetX));

            &.active {
                cursor: ne-resize;
            }
        }

        &.sw {
            bottom: calc(0px - var(--offsetY));
            left: calc(0px - var(--offsetX));

            &.active {
                cursor: sw-resize;
            }
        }

        &.se {
            bottom: calc(0px - var(--offsetY));
            right: calc(0px - var(--offsetX));

            &.active {
                cursor: se-resize;
            }
        }
    }
</style>
