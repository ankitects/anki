<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { on } from "../lib/events";
    import { Callback, singleCallback } from "../lib/typing";
    import type { HeightResizable } from "./resizable";
    import { horizontalHandle } from "./icons";
    import IconConstrain from "./IconConstrain.svelte";

    export let components: HeightResizable[];
    export let index = 0;
    export let clientHeight: number;

    let destroy: Callback;

    let before: HeightResizable;
    let after: HeightResizable;

    function onMove(this: Window, { movementY }: PointerEvent): void {
        if (movementY < 0) {
            const resized = before.height.resize(movementY);
            after.height.resize(-resized);
        } else if (movementY > 0) {
            const resized = after.height.resize(-movementY);
            before.height.resize(-resized);
        }
    }

    let minHeight: number;

    function releasePointer(this: Window): void {
        destroy();
        document.exitPointerLock();

        const resizerAmount = components.length - 1;
        const componentsHeight = clientHeight - minHeight * resizerAmount;

        for (const component of components) {
            component.height.stop(componentsHeight, components.length);
        }
    }

    function lockPointer(this: HTMLHRElement) {
        this.requestPointerLock();

        before = components[index];
        after = components[index + 1];

        for (const component of components) {
            component.height.start();
        }

        destroy = singleCallback(
            on(window, "pointermove", onMove),
            on(window, "pointerup", releasePointer),
        );
    }
</script>

<div
    bind:clientHeight={minHeight}
    class="horizontal-resizer"
    on:pointerdown|preventDefault={lockPointer}
>
    <div class="drag-handle">
        <IconConstrain iconSize={80}>{@html horizontalHandle}</IconConstrain>
    </div>
</div>

<style lang="scss">
    .horizontal-resizer {
        width: 100%;
        cursor: row-resize;
        position: relative;

        z-index: 20;
        .drag-handle {
            position: absolute;
            left: 50%;
            top: 50%;
            transform: translate(-50%, -50%);
            opacity: 0.4;
        }

        &::before {
            content: "";
            position: absolute;
            height: 10px;
            top: -5px;
            left: 0;
            width: 100%;
        }
        &:hover .drag-handle {
            opacity: 0.8;
        }
    }
</style>
