<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script context="module" lang="ts">
    import { writable } from "svelte/store";

    const changeSignal = writable(Symbol());

    export function emitChangeSignal() {
        changeSignal.set(Symbol());
    }
</script>

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

    export let mode: IOMode;
    const iconSize = 80;
    let innerWidth = 0;
    const startingTool = mode.kind === "add" ? "draw-rectangle" : "cursor";
    let canvas: fabric.Canvas | null = null;

    $: {
        globalThis.maskEditor = canvas ? new MaskEditorAPI(canvas) : null;
    }

    const dispatch = createEventDispatcher();

    function onChange() {
        dispatch("change", { canvas });
    }

    function onImageLoaded({ path, noteId }: ImageLoadedEvent) {
        dispatch("image-loaded", { path, noteId });
    }

    $: $changeSignal, onChange();

    function init(_node: HTMLDivElement) {
        if (mode.kind == "add") {
            setupMaskEditor(mode.imagePath, onChange, onImageLoaded).then((canvas1) => {
                canvas = canvas1;
            });
        } else {
            setupMaskEditorForEdit(mode.noteId, onChange, onImageLoaded).then(
                (canvas1) => {
                    canvas = canvas1;
                },
            );
        }
    }

    onMount(() => {
        window.addEventListener("resize", resizeEvent);
    });

    onDestroy(() => {
        window.removeEventListener("resize", resizeEvent);
    });

    const resizeEvent = () => {
        onResize(canvas!);
    };
</script>

<Toolbar {canvas} {iconSize} activeTool={startingTool} />
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
        outline: none !important;
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
