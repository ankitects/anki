<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import IconButton from "../components/IconButton.svelte";
    import { drawEllipse, drawPolygon, drawRectangle } from "./tools/index";
    import { enableSelectable, stopDraw } from "./tools/lib";
    import { tools } from "./tools/tool-buttons";
    import TopToolbar from "./TopToolbar.svelte";

    export let instance;
    export let canvas;

    const iconSize = 80;

    export let activeTool = "cursor";

    // handle tool changes after initialization
    $: if (instance && canvas) {
        disableFunctions();
        enableSelectable(canvas, true);

        switch (activeTool) {
            case "magnify":
                enableSelectable(canvas, false);
                instance.resume();
                break;
            case "draw-rectangle":
                drawRectangle(canvas);
                break;
            case "draw-ellipse":
                drawEllipse(canvas);
                break;
            case "draw-polygon":
                drawPolygon(canvas, instance);
                break;
            default:
                break;
        }
    }

    const disableFunctions = () => {
        instance.pause();
        stopDraw(canvas);
        canvas.selectionColor = "rgba(100, 100, 255, 0.3)";
    };
</script>

<TopToolbar {canvas} {instance} {iconSize} />

<div class="tool-bar-container">
    {#each tools as tool}
        <IconButton
            class="tool-icon-button {activeTool == tool.id ? 'active-tool' : ''}"
            {iconSize}
            active={activeTool === tool.id}
            on:click={() => {
                activeTool = tool.id;
            }}
        >
            {@html tool.icon}
        </IconButton>
    {/each}
</div>

<style>
    .tool-bar-container {
        position: fixed;
        top: 42px;
        left: 2px;
        height: 100%;
        border-right: 1px solid var(--border);
        overflow-y: auto;
        width: 32px;
        z-index: 99;
        background: var(--canvas-elevated);
        padding-bottom: 100px;
    }

    :global(.tool-icon-button) {
        border: unset;
        display: block;
        width: 32px;
        height: 32px;
        margin: unset;
        padding: 6px !important;
    }

    :global(.active-tool) {
        color: white !important;
        background: var(--button-primary-bg) !important;
    }

    ::-webkit-scrollbar {
        width: 0.2em !important;
        height: 0.2em !important;
    }
</style>
