<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { on } from "../lib/events";
    import { Callback, singleCallback } from "../lib/typing";
    import IconConstrain from "./IconConstrain.svelte";
    import { horizontalHandle } from "./icons";
    import type { HeightResizable } from "./resizable";

    type Pane = {
        resizable: HeightResizable;
        height: number;
        minHeight: number;
        maxHeight: number;
    };
    export let panes: Pane[];
    export let index = 0;
    export let clientHeight: number;

    let destroy: Callback;

    let before: Pane;
    let after: Pane;

    $: resizerAmount = panes.length - 1;
    $: componentsHeight = clientHeight - resizerHeight * resizerAmount;

    export function move(targets: Pane[], targetHeight: number): void {
        const [resizeTarget, resizePartner] = targets;
        if (targetHeight <= resizeTarget.maxHeight) {
            resizeTarget.resizable.height.setSize(targetHeight);
            resizePartner.resizable.height.setSize(componentsHeight - targetHeight);
        }
    }

    export function collapse(target: Pane) {
        target.resizable.height.setSize(target.minHeight);
        target.height = target.minHeight;
    }

    // not yet safe to use for Infinity sized panes
    export function expand(target: Pane) {
        target.resizable.height.setSize(target.maxHeight);
        target.height = target.maxHeight;
    }

    function onMove(this: Window, { movementY }: PointerEvent): void {
        if (movementY < 0) {
            if (after.height - movementY <= after.maxHeight) {
                const resized = before.resizable.height.resize(movementY);
                after.resizable.height.resize(-resized);
            } else {
                const resized = before.resizable.height.resize(
                    after.height - after.maxHeight,
                );
                after.resizable.height.resize(-resized);
            }
        } else if (before.height + movementY <= before.maxHeight) {
            const resized = after.resizable.height.resize(-movementY);
            before.resizable.height.resize(-resized);
        } else {
            const resized = after.resizable.height.resize(
                before.height - before.maxHeight,
            );
            before.resizable.height.resize(-resized);
        }
    }

    let resizerHeight: number;

    function releasePointer(this: Window): void {
        destroy();
        document.exitPointerLock();

        for (const pane of panes) {
            pane.resizable.height.stop(componentsHeight, panes.length);
        }
    }

    function lockPointer(this: HTMLDivElement) {
        try {
            this.requestPointerLock();

            before = panes[index];
            after = panes[index + 1];

            for (const pane of panes) {
                pane.resizable.height.start();
            }

            destroy = singleCallback(
                on(window, "pointermove", onMove),
                on(window, "pointerup", releasePointer),
            );
        } catch (e) {
            if (e instanceof DOMException) {
                return;
            } else {
                throw e;
            }
        }
    }
</script>

<div
    bind:clientHeight={resizerHeight}
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
        height: 10px;
        border-top: 1px solid var(--border-subtle);

        z-index: 20;
        .drag-handle {
            position: absolute;
            left: 50%;
            top: 50%;
            transform: translate(-50%, -50%);
            opacity: 0.4;
        }
        &:hover .drag-handle {
            opacity: 0.8;
        }
    }
</style>
