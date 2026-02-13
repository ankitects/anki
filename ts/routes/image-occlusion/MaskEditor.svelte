<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->

<script lang="ts">
    import type { fabric } from "fabric";
    import { createEventDispatcher, onDestroy, onMount } from "svelte";

    import type { IOMode } from "./lib";
    import {
        type ImageLoadedEvent,
        setupMaskEditor,
        setupMaskEditorForEdit,
    } from "./mask-editor";
    import Toolbar from "./Toolbar.svelte";
    import { MaskEditorAPI } from "./tools/api";
    import { onResize } from "./tools/tool-zoom";
    import { saveNeededStore } from "./store";

    export let mode: IOMode;
    const iconSize = 80;
    let innerWidth = 0;
    const startingTool = mode.kind === "add" ? "draw-rectangle" : "cursor";
    let canvas: fabric.Canvas | null = null;

    $: {
        globalThis.maskEditor = canvas ? new MaskEditorAPI(canvas) : null;
    }

    const dispatch = createEventDispatcher();

    function onImageLoaded({ path, noteId }: ImageLoadedEvent) {
        dispatch("image-loaded", { path, noteId });
    }

    const unsubscribe = saveNeededStore.subscribe((saveNeeded: boolean) => {
        if (saveNeeded === false) {
            return;
        }
        dispatch("save");
        saveNeededStore.set(false);
    });

    function init(_node: HTMLDivElement) {
        if (mode.kind == "add") {
            if ("clonedNoteId" in mode) {
                // Editing occlusions on a new note cloned from an existing note via "Create copy"
                setupMaskEditorForEdit(mode.clonedNoteId, onImageLoaded).then(
                    (canvas1) => {
                        canvas = canvas1;
                    },
                );
            } else {
                // Editing occlusions on a new note through the "Add" window
                setupMaskEditor(mode.imagePath, onImageLoaded).then((canvas1) => {
                    canvas = canvas1;
                });
            }
        } else {
            // Editing occlusions on an existing note through the "Browser" window
            setupMaskEditorForEdit(mode.noteId, onImageLoaded).then((canvas1) => {
                canvas = canvas1;
            });
        }
    }

    onMount(() => {
        window.addEventListener("resize", resizeEvent);
    });

    onDestroy(() => {
        window.removeEventListener("resize", resizeEvent);
        unsubscribe();
    });

    const resizeEvent = () => {
        if (canvas === null) {
            return;
        }
        onResize(canvas);
    };
</script>

<Toolbar {canvas} {iconSize} activeTool={startingTool} />
<div class="editor-main" bind:clientWidth={innerWidth}>
    <div class="editor-container" use:init>
        <!-- svelte-ignore a11y-missing-attribute -->
        <img id="image" />
        <canvas id="canvas"></canvas>
    </div>
</div>

<style lang="scss">
    .editor-main {
        position: relative;
        bottom: 2px;
        right: 2px;
        overflow: auto;
        outline: none !important;
        height: 88vh;
    }

    :global([dir="rtl"]) .editor-main {
        left: 2px;
        right: 36px;
    }

    .editor-container {
        width: 100%;
        height: 100%;
        position: relative;
        direction: ltr;
        overflow: hidden;
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
