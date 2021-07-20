<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import { getContext } from "svelte";
    import { nightModeKey } from "components/context-keys";

    export let hidden: boolean;

    export let top: number = 0;
    export let left: number = 0;
    export let width: number = 0;
    export let height: number = 0;

    function setPointerCapture(event: PointerEvent): void {
        if (event.pointerId !== 1) {
            return;
        }

        (event.target as Element).setPointerCapture(event.pointerId);
    }

    function resize(event: PointerEvent): void {
        if (!(event.target as Element).hasPointerCapture(event.pointerId)) {
            return;
        }
    }

    const nightMode = getContext(nightModeKey);
</script>

<div
    style="--top: {top}px; --left: {left}px; --width: {width}px; --height: {height}px;"
    class="image-handle-selection"
    {hidden}
>
    <div class="image-handle-bg" />
    <div class:nightMode class="image-handle-control image-handle-control-nw" />
    <div class:nightMode class="image-handle-control image-handle-control-ne" />
    <div class:nightMode class="image-handle-control image-handle-control-sw" />
    <div
        class:nightMode
        class="image-handle-control image-handle-control-se is-active"
        on:pointerdown={setPointerCapture}
        on:pointermove={resize}
    />
</div>

<style lang="scss">
    div {
        position: absolute;
    }

    .image-handle-selection {
        top: var(--top);
        left: var(--left);
        width: var(--width);
        height: var(--height);
    }

    .image-handle-bg {
        width: 100%;
        height: 100%;
        background-color: black;
        opacity: 0.2;
    }

    .image-handle-control {
        width: 7px;
        height: 7px;
        border: 1px solid black;

        &.is-active {
            background-color: black;
        }

        &.nightMode {
            border-color: white;

            &.is-active {
                background-color: white;
            }
        }
    }

    .image-handle-control-nw {
        top: -5px;
        left: -5px;
        border-bottom: none;
        border-right: none;
    }

    .image-handle-control-ne {
        top: -5px;
        right: -5px;
        border-bottom: none;
        border-left: none;
    }

    .image-handle-control-sw {
        bottom: -5px;
        left: -5px;
        border-top: none;
        border-right: none;
    }

    .image-handle-control-se {
        bottom: -5px;
        right: -5px;
        border-top: none;
        border-left: none;

        &.is-active {
            cursor: se-resize;
        }
    }
</style>
