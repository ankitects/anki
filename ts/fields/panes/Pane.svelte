<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { writable } from "svelte/store";

    import { resizable } from "../resizable";

    export let baseSize = 600;

    const resizes = writable(false);
    const paneSize = writable(baseSize);

    const [
        { resizesDimension: resizesWidth, resizedDimension: resizedWidth },
        widthAction,
        width,
    ] = resizable(baseSize, resizes, paneSize);
    const [
        { resizesDimension: resizesHeight, resizedDimension: resizedHeight },
        heightAction,
        height,
    ] = resizable(baseSize, resizes, paneSize);

    export { height, width };
</script>

<div
    class="pane"
    class:resize={$resizes}
    class:resize-width={$resizesWidth}
    class:resize-height={$resizesHeight}
    style:--pane-size={$paneSize}
    style:--resized-width="{$resizedWidth}px"
    style:--resized-height="{$resizedHeight}px"
    on:focusin
    on:pointerdown
    use:widthAction={(element) => element.offsetWidth}
    use:heightAction={(element) => element.offsetHeight}
>
    <slot />
</div>

<style lang="scss">
    @use "./panes" as panes;

    .pane {
        @include panes.resizable(column, true, true);
    }
</style>
