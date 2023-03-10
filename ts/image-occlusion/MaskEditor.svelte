<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import type { PanZoom } from "panzoom";
    import panzoom from "panzoom";

    import { setupMaskEditor, setupMaskEditorForEdit } from "./mask-editor";
    import SideToolbar from "./SideToolbar.svelte";

    export let path: string | null;
    export let noteId: number | null;

    let instance: PanZoom;
    let innerWidth = 0;
    $: canvas = null;

    function initPanzoom(node) {
        instance = panzoom(node, {
            bounds: true,
            maxZoom: 3,
            minZoom: 0.1,
            zoomDoubleClickSpeed: 1,
            smoothScroll: false,
        });
        instance.pause();

        if (path) {
            setupMaskEditor(path, instance).then((canvas1) => {
                canvas = canvas1;
            });
        }

        if (noteId) {
            setupMaskEditorForEdit(noteId, instance).then((canvas1) => {
                canvas = canvas1;
            });
        }
    }
</script>

<SideToolbar {instance} {canvas} />
<div class="editor-main" bind:clientWidth={innerWidth}>
    <div class="editor-container" use:initPanzoom>
        <canvas id="canvas" />
    </div>
</div>

<style lang="scss">
    .editor-main {
        position: absolute;
        top: 84px;
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
    }

    :global(.upper-canvas) {
        border: 0.5px solid var(--border-strong);
    }
</style>
