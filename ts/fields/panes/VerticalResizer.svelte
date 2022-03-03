<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { on } from "../../lib/events";
    import { Callback, singleCallback } from "../../lib/typing";
    import type { WidthResizable } from "./resizable";

    export let components: WidthResizable[];
    export let index: number;
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

    const minWidth = 4;

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

<hr
    class="vertical-resizer"
    style:--min-width="{minWidth}px"
    on:pointerdown|preventDefault={lockPointer}
/>

<style lang="scss">
    @use "sass/elevation" as elevation;
    @use "./panes" as panes;

    .vertical-resizer {
        min-width: var(--min-width);
        cursor: col-resize;

        @include panes.reset-hr();
        height: unset;

        $vertical-bars: 'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="10" height="30"><path d="M2 0 v30 M5 0 v30 M8 0 v30" fill="none" stroke="black"/></svg>';
        @include panes.resize-handle-background($vertical-bars);

        @include elevation.elevation(2);
        z-index: 20;
    }
</style>
