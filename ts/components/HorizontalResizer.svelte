<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { on } from "../lib/events";
    import { Callback, singleCallback } from "../lib/typing";
    import type { HeightResizable } from "./resizable";

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

    const minHeight = 9;

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

<hr
    class="horizontal-resizer"
    style:--min-height="{minHeight}px"
    on:pointerdown|preventDefault={lockPointer}
/>

<style lang="scss">
    @use "sass/elevation" as elevation;
    @use "panes" as panes;

    .horizontal-resizer {
        min-height: var(--min-height);
        cursor: row-resize;

        @include panes.reset-hr();

        $horizontal-bars: 'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="30" height="10"><path d="M0 2 h30 M0 5 h30 M0 8 h30" fill="none" stroke="black"/></svg>';
        @include panes.resize-handle-background($horizontal-bars);
        z-index: 20;
    }
</style>
