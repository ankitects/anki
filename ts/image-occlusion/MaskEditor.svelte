<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import type { PanZoom } from "panzoom";
    import panzoom from "panzoom";

    import type { IOMode } from "./lib";
    import { setupMaskEditor, setupMaskEditorForEdit } from "./mask-editor";
    import SideToolbar from "./SideToolbar.svelte";

    export let mode: IOMode;

    let instance: PanZoom;
    let innerWidth = 0;
    const startingTool = mode.kind === "add" ? "draw-rectangle" : "cursor";
    $: canvas = null;

    function init(node) {
        instance = panzoom(node, {
            bounds: true,
            maxZoom: 3,
            minZoom: 0.1,
            zoomDoubleClickSpeed: 1,
            smoothScroll: false,
        });
        instance.pause();

        if (mode.kind == "add") {
            setupMaskEditor(mode.imagePath, instance).then((canvas1) => {
                canvas = canvas1;
            });
        } else {
            setupMaskEditorForEdit(mode.noteId, instance).then((canvas1) => {
                canvas = canvas1;
            });
        }
    }
</script>

<SideToolbar {instance} {canvas} activeTool={startingTool} />
<div class="editor-main" bind:clientWidth={innerWidth}>
    <div class="editor-container" use:init>
        <!-- svelte-ignore a11y-missing-attribute -->
        <img id="image" />
        <canvas id="canvas" />
    </div>
</div>

<style lang="scss">
    .editor-main {
        position: absolute;
        top: 42px;
        left: 36px;
        bottom: 2px;
        right: 2px;
        border: 1px solid var(--border);
        overflow: auto;
        padding-bottom: 100px;
    }

    .editor-container {
        width: 100%;
        height: 100%;
        position: relative;
    }

    #image {
        position: absolute;
    }

    :global(.upper-canvas) {
        border: 0.5px solid var(--border-strong);
        border-width: thin;
    }

    :global(.canvas-container) {
        position: absolute;
    }
</style>
