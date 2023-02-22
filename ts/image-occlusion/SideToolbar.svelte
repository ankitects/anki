<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "@tslib/ftl";

    import IconButton from "../components/IconButton.svelte";
    import ColorPicker from "./color-picker/ColorPicker.svelte";
    import { mdiFormatColorFill, mdiPalette } from "./icons";
    import { drawEllipse, drawPolygon, drawRectangle } from "./tools/index";
    import {
        enableSelectable,
        fillQuestionMaskColor,
        fillShapeColor,
        getQuestionMaskColor,
        getShapeColor,
        stopDraw,
    } from "./tools/lib";
    import { tools } from "./tools/tool-buttons";
    import TopToolbar from "./TopToolbar.svelte";

    export let instance;
    export let canvas;

    const iconSize = 80;

    let activeTool = "cursor";
    let showChooseMaskColor = false;
    let showChooseShapeColor = false;

    function setActive(toolId) {
        activeTool = toolId;
        disableFunctions();
        document.removeEventListener("click", fillColorEventListener);
        document.removeEventListener("click", questionMaskColorEventListener);

        switch (toolId) {
            case "cursor":
                enableSelectable(canvas, true);
                break;
            case "magnify":
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
            case "shape-fill-color":
                {
                    showChooseShapeColor = !showChooseShapeColor;
                    showChooseMaskColor = false;
                    enableSelectable(canvas, true);
                    document.addEventListener("click", fillColorEventListener);
                }
                break;
            case "choose-color":
                {
                    showChooseMaskColor = !showChooseMaskColor;
                    showChooseShapeColor = false;
                    enableSelectable(canvas, true);
                    document.addEventListener("click", questionMaskColorEventListener);
                }
                break;
            default:
                break;
        }
    }

    const disableFunctions = () => {
        instance.pause();
        stopDraw(canvas);
        enableSelectable(canvas, false);
    };

    const fillColorEventListener = () => {
        fillShapeColor(canvas);
    };
    const questionMaskColorEventListener = () => {
        fillQuestionMaskColor(canvas);
    };

    document.addEventListener("click", (e) => {
        const target = e.target as HTMLElement;
        if (target.classList.contains("upper-canvas")) {
            showChooseShapeColor = false;
            showChooseMaskColor = false;
        }
    });
</script>

<TopToolbar {canvas} {activeTool} {instance} {iconSize} />

<div class="tool-bar-container">
    {#each tools as tool}
        <IconButton
            class="tool-icon-button {activeTool == tool.id ? 'active-tool' : ''}"
            {iconSize}
            active={activeTool === tool.id}
            on:click={() => {
                setActive(tool.id);
            }}>{@html tool.icon}</IconButton
        >
    {/each}

    <IconButton
        class="tool-icon-button color-picker-1 {activeTool == 'shape-fill-color'
            ? 'active-tool'
            : ''}"
        {iconSize}
        active={activeTool === "shape-fill-color"}
        on:click={() => {
            activeTool = "shape-fill-color";
            setActive(activeTool);
        }}>{@html mdiFormatColorFill}</IconButton
    >
    <IconButton
        class="tool-icon-button color-picker-2 {activeTool == 'choose-color'
            ? 'active-tool'
            : ''}"
        {iconSize}
        active={activeTool === "choose-color"}
        on:click={() => {
            activeTool = "choose-color";
            setActive(activeTool);
        }}>{@html mdiPalette}</IconButton
    >
</div>

<ColorPicker
    show={showChooseShapeColor}
    top={120}
    left={36}
    title={tr.notetypesChangeShapeColor()}
    selectedColor={getShapeColor()}
    saveColor={(color) => {
        localStorage.setItem("shape-color", color);
    }}
/>

<ColorPicker
    show={showChooseMaskColor}
    top={140}
    left={36}
    title={tr.notetypesQuestionMaskColor()}
    selectedColor={getQuestionMaskColor()}
    saveColor={(color) => {
        localStorage.setItem("ques-color", color);
    }}
/>

<style>
    .tool-bar-container {
        position: fixed;
        top: 46px;
        left: 0;
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
        color: red !important;
        background: unset !important;
    }

    ::-webkit-scrollbar {
        width: 0.2em !important;
        height: 0.2em !important;
    }
</style>
