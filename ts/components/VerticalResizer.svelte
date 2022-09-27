<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { on } from "../lib/events";
    import { Callback, singleCallback } from "../lib/typing";
    import IconConstrain from "./IconConstrain.svelte";
    import { verticalHandle } from "./icons";
    import type { WidthResizable } from "./resizable";

    export let components: WidthResizable[];
    export let index = 0;
    export let clientWidth: number;

    let destroy: Callback;

    let before: WidthResizable;
    let after: WidthResizable;

    function onMove(this: Window, { movementX }: PointerEvent): void {
        if (movementX < 0) {
            const resized = before.width.resize(movementX);
            after.width.resize(-resized);
        } else if (movementX > 0) {
            const resized = after.width.resize(-movementX);
            before.width.resize(-resized);
        }
    }

    let minWidth: number;

    function releasePointer(this: Window): void {
        destroy();
        document.exitPointerLock();

        const resizerAmount = components.length - 1;
        const componentsWidth = clientWidth - minWidth * resizerAmount;

        for (const component of components) {
            component.width.stop(componentsWidth, components.length);
        }
    }

    function lockPointer(this: HTMLHRElement) {
        this.requestPointerLock();

        before = components[index];
        after = components[index + 1];

        for (const component of components) {
            component.width.start();
        }

        destroy = singleCallback(
            on(window, "pointermove", onMove),
            on(window, "pointerup", releasePointer),
        );
    }
</script>

<div
    bind:clientWidth={minWidth}
    class="vertical-resizer"
    on:pointerdown|preventDefault={lockPointer}
>
    <div class="drag-handle">
        <IconConstrain iconSize={80}>{@html verticalHandle}</IconConstrain>
    </div>
</div>

<style lang="scss">
    .vertical-resizer {
        height: 100%;
        cursor: col-resize;
        position: relative;

        z-index: 20;
        .drag-handle {
            position: absolute;
            top: 50%;
            left: 50%;
            transform: translate(-50%, -50%);
            opacity: 0.4;
        }

        &::before {
            content: "";
            position: absolute;
            width: 10px;
            left: -5px;
            top: 0;
            height: 100%;
        }
        &:hover .drag-handle {
            opacity: 0.8;
        }
    }
</style>
