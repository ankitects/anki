<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { on } from "@tslib/events";
    import type { Callback} from "@tslib/typing";
    import { singleCallback } from "@tslib/typing";
    import { createEventDispatcher } from "svelte";
    import { fly } from "svelte/transition";
    
    import IconConstrain from "./IconConstrain.svelte";
    import { horizontalHandle } from "./icons";
    import type { ResizablePane } from "./types";

    export let panes: ResizablePane[];
    export let index = 0;
    export let tip = "";
    export let showIndicator = false;
    export let clientHeight: number;

    const rtl = window.getComputedStyle(document.body).direction == "rtl";

    const dispatch = createEventDispatcher();

    let destroy: Callback;

    let before: ResizablePane;
    let after: ResizablePane;

    $: resizerAmount = panes.length - 1;
    $: componentsHeight = clientHeight - resizerHeight * resizerAmount;

    export function move(targets: ResizablePane[], targetHeight: number): void {
        const [resizeTarget, resizePartner] = targets;
        if (targetHeight <= resizeTarget.maxHeight) {
            resizeTarget.resizable.getHeightResizer().setSize(targetHeight);
            resizePartner.resizable
                .getHeightResizer()
                .setSize(componentsHeight - targetHeight);
        }
    }

    function onMove(this: Window, { movementY }: PointerEvent): void {
        if (movementY < 0) {
            if (after.height - movementY <= after.maxHeight) {
                const resized = before.resizable.getHeightResizer().resize(movementY);
                after.resizable.getHeightResizer().resize(-resized);
            } else {
                const resized = before.resizable
                    .getHeightResizer()
                    .resize(after.height - after.maxHeight);
                after.resizable.getHeightResizer().resize(-resized);
            }
        } else if (before.height + movementY <= before.maxHeight) {
            const resized = after.resizable.getHeightResizer().resize(-movementY);
            before.resizable.getHeightResizer().resize(-resized);
        } else {
            const resized = after.resizable
                .getHeightResizer()
                .resize(before.height - before.maxHeight);
            before.resizable.getHeightResizer().resize(-resized);
        }
    }

    let resizerHeight: number;

    function releasePointer(this: Window): void {
        destroy();
        document.exitPointerLock();

        for (const pane of panes) {
            pane.resizable.getHeightResizer().stop(componentsHeight, panes.length);
        }
    }

    function lockPointer(this: HTMLDivElement) {
        this.requestPointerLock();

        before = panes[index];
        after = panes[index + 1];

        for (const pane of panes) {
            pane.resizable.getHeightResizer().start();
        }

        destroy = singleCallback(
            on(window, "pointermove", onMove),
            on(window, "pointerup", () => {
                releasePointer.call(window);
                dispatch("release");
            }),
        );
    }
</script>

<div
    class="horizontal-resizer"
    class:rtl
    title={tip}
    bind:clientHeight={resizerHeight}
    on:pointerdown|preventDefault={lockPointer}
    on:dblclick|preventDefault
>
    {#if showIndicator}
        <div
            class="resize-indicator"
            transition:fly={{ x: rtl ? 25 : -25, duration: 200 }}
        >
            <slot />
        </div>
    {/if}

    <div class="drag-handle">
        <IconConstrain iconSize={80}>{@html horizontalHandle}</IconConstrain>
    </div>
</div>

<style lang="scss">
    .horizontal-resizer {
        width: 100%;
        cursor: row-resize;
        position: relative;
        height: 25px;
        border-top: 1px solid var(--border);

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

        .resize-indicator {
            position: absolute;
            font-size: small;
            bottom: 0;
        }
        &.rtl .resize-indicator {
            padding: 0.5rem 0 0 0.5rem;
            right: 0;
        }
    }
</style>
