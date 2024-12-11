<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "@generated/ftl";
    import { directionKey } from "@tslib/context-keys";
    import { on } from "@tslib/events";
    import { getPlatformString } from "@tslib/shortcuts";
    import type { Callback } from "@tslib/typing";
    import { singleCallback } from "@tslib/typing";
    import { getContext, onDestroy, onMount } from "svelte";
    import type { Readable } from "svelte/store";

    import DropdownItem from "$lib/components/DropdownItem.svelte";
    import Icon from "$lib/components/Icon.svelte";
    import IconButton from "$lib/components/IconButton.svelte";
    import {
        mdiEye,
        mdiFormatAlignCenter,
        mdiSquare,
        mdiViewDashboard,
    } from "$lib/components/icons";
    import Popover from "$lib/components/Popover.svelte";
    import Shortcut from "$lib/components/Shortcut.svelte";
    import WithFloating from "$lib/components/WithFloating.svelte";

    import {
        hideAllGuessOne,
        ioMaskEditorVisible,
        textEditingState,
        saveNeededStore,
        opacityStateStore,
    } from "./store";
    import { drawEllipse, drawPolygon, drawRectangle, drawText } from "./tools/index";
    import { makeMaskTransparent } from "./tools/lib";
    import { enableSelectable, stopDraw } from "./tools/lib";
    import {
        alignTools,
        deleteDuplicateTools,
        groupUngroupTools,
        zoomTools,
    } from "./tools/more-tools";
    import { toggleTranslucentKeyCombination } from "./tools/shortcuts";
    import { tools } from "./tools/tool-buttons";
    import { drawCursor } from "./tools/tool-cursor";
    import { removeUnfinishedPolygon } from "./tools/tool-polygon";
    import { undoRedoTools, undoStack } from "./tools/tool-undo-redo";
    import {
        disablePan,
        disableZoom,
        enablePan,
        enableZoom,
        onWheelDrag,
        onWheelDragX,
    } from "./tools/tool-zoom";

    export let canvas;
    export let iconSize;
    export let activeTool = "cursor";
    let showAlignTools = false;
    let leftPos = 82;
    let maskOpacity = false;
    let showFloating = false;
    const direction = getContext<Readable<"ltr" | "rtl">>(directionKey);
    // handle zoom event when mouse scroll and ctrl key are hold for panzoom
    let spaceClicked = false;
    let controlClicked = false;
    let shiftClicked = false;
    let move = false;
    const spaceKey = " ";
    const controlKey = "Control";
    const shiftKey = "Shift";
    let removeHandlers: Callback;

    function onClick(event: MouseEvent) {
        const upperCanvas = document.querySelector(".upper-canvas");
        if (event.target == upperCanvas) {
            showAlignTools = false;
        }
    }

    function onMousemove() {
        if (spaceClicked || move) {
            disableFunctions();
            enablePan(canvas);
        }
    }

    function onMouseup() {
        if (spaceClicked) {
            spaceClicked = false;
        }
        if (move) {
            move = false;
        }
    }

    function onKeyup(event: KeyboardEvent) {
        if (
            event.key === spaceKey ||
            event.key === controlKey ||
            event.key === shiftKey
        ) {
            spaceClicked = false;
            controlClicked = false;
            shiftClicked = false;
            move = false;

            disableFunctions();
            handleToolChanges(activeTool);
        }
    }

    function onKeydown(event: KeyboardEvent) {
        if (event.key === spaceKey) {
            spaceClicked = true;
        }
        if (event.key === controlKey) {
            controlClicked = true;
        }
        if (event.key === shiftKey) {
            shiftClicked = true;
        }
    }

    function onWheel(event: WheelEvent) {
        // allow scroll in fields, when mask editor hidden
        if(!$ioMaskEditorVisible) {
            return
        }

        if (event.ctrlKey) {
            controlClicked = true;
        }
        if (event.shiftKey) {
            shiftClicked = true;
        }

        event.preventDefault();

        if (controlClicked) {
            disableFunctions();
            enableZoom(canvas);
            return;
        }

        if (shiftClicked) {
            onWheelDragX(canvas, event);
            return;
        }

        onWheelDrag(canvas, event);
    }

    // initializes lastPosX and lastPosY because it is undefined in touchmove event
    function onTouchstart(event: TouchEvent) {
        const canvas = globalThis.canvas;
        canvas.lastPosX = event.touches[0].clientX;
        canvas.lastPosY = event.touches[0].clientY;
    }

    // initializes lastPosX and lastPosY because it is undefined before mousemove event
    function onMousemoveDocument(event: MouseEvent) {
        if (spaceClicked) {
            canvas.lastPosX = event.clientX;
            canvas.lastPosY = event.clientY;
        }
    }

    const handleToolChanges = (newActiveTool: string) => {
        disableFunctions();
        enableSelectable(canvas, true);
        // remove unfinished polygon when switching to other tools
        removeUnfinishedPolygon(canvas);

        switch (newActiveTool) {
            case "cursor":
                drawCursor(canvas);
                break;
            case "draw-rectangle":
                drawRectangle(canvas);
                break;
            case "draw-ellipse":
                drawEllipse(canvas);
                break;
            case "draw-polygon":
                drawPolygon(canvas);
                break;
            case "draw-text":
                drawText(canvas, () => {
                    activeTool = "cursor";
                    handleToolChanges(activeTool);
                });
                break;
        }
    };

    // handle tool changes after initialization
    $: if (canvas) {
        handleToolChanges(activeTool);
    }

    const disableFunctions = () => {
        stopDraw(canvas);
        disableZoom(canvas);
        disablePan(canvas);
    };

    function changeOcclusionType(occlusionType: "all" | "one"): void {
        $hideAllGuessOne = occlusionType === "all";
        saveNeededStore.set(true);
    }

    onMount(() => {
        opacityStateStore.set(maskOpacity);
        removeHandlers = singleCallback(
            on(document, "click", onClick),
            on(window, "mousemove", onMousemove),
            on(window, "mouseup", onMouseup),
            on(window, "keyup", onKeyup),
            on(window, "keydown", onKeydown),
            on(window, "wheel", onWheel, { passive: false }),
            on(document, "touchstart", onTouchstart),
            on(document, "mousemove", onMousemoveDocument),
        );
    });

    onDestroy(() => {
        removeHandlers();
    });
</script>

<div class="tool-bar-container">
    {#each tools as tool}
        <IconButton
            class="tool-icon-button {activeTool == tool.id ? 'active-tool' : ''}"
            {iconSize}
            tooltip="{tool.tooltip()} ({getPlatformString(tool.shortcut)})"
            active={activeTool === tool.id}
            on:click={() => {
                activeTool = tool.id;
                handleToolChanges(activeTool);
            }}
        >
            <Icon icon={tool.icon} />
        </IconButton>
        {#if $ioMaskEditorVisible && !$textEditingState}
            <Shortcut
                keyCombination={tool.shortcut}
                on:action={() => {
                    activeTool = tool.id;
                    handleToolChanges(activeTool);
                }}
            />
        {/if}
    {/each}
</div>

<div dir={$direction}>
    <div class="top-tool-bar-container">
        <WithFloating
            show={showFloating}
            closeOnInsideClick
            inline
            on:close={() => (showFloating = false)}
        >
            <IconButton
                class="top-tool-icon-button border-radius dropdown-tool-mode"
                slot="reference"
                tooltip={tr.editingImageOcclusionMode()}
                {iconSize}
                on:click={() => (showFloating = !showFloating)}
            >
                <Icon icon={$hideAllGuessOne ? mdiViewDashboard : mdiSquare} />
            </IconButton>

            <Popover slot="floating">
                <DropdownItem
                    active={$hideAllGuessOne}
                    on:click={() => changeOcclusionType("all")}
                >
                    <span>{tr.notetypesHideAllGuessOne()}</span>
                </DropdownItem>
                <DropdownItem
                    active={!$hideAllGuessOne}
                    on:click={() => changeOcclusionType("one")}
                >
                    <span>{tr.notetypesHideOneGuessOne()}</span>
                </DropdownItem>
            </Popover>
        </WithFloating>

        <!-- undo & redo tools -->
        <div class="undo-redo-button">
            {#each undoRedoTools as tool}
                <IconButton
                    class="top-tool-icon-button {tool.name === 'undo'
                        ? 'left-border-radius'
                        : 'right-border-radius'}"
                    {iconSize}
                    on:click={tool.action}
                    tooltip="{tool.tooltip()} ({getPlatformString(tool.shortcut)})"
                    disabled={tool.name === "undo"
                        ? !$undoStack.undoable
                        : !$undoStack.redoable}
                >
                    <Icon icon={tool.icon} />
                </IconButton>
                {#if $ioMaskEditorVisible && !$textEditingState}
                    <Shortcut keyCombination={tool.shortcut} on:action={tool.action} />
                {/if}
            {/each}
        </div>

        <!-- zoom tools -->
        <div class="tool-button-container">
            {#each zoomTools as tool}
                <IconButton
                    class="top-tool-icon-button {tool.name === 'zoomOut'
                        ? 'left-border-radius'
                        : ''} {tool.name === 'zoomReset' ? 'right-border-radius' : ''}"
                    {iconSize}
                    tooltip="{tool.tooltip()} ({getPlatformString(tool.shortcut)})"
                    on:click={() => {
                        tool.action(canvas);
                    }}
                >
                    <Icon icon={tool.icon} />
                </IconButton>
                {#if $ioMaskEditorVisible && !$textEditingState}
                    <Shortcut
                        keyCombination={tool.shortcut}
                        on:action={() => {
                            tool.action(canvas);
                        }}
                    />
                {/if}
            {/each}
        </div>

        <div class="tool-button-container">
            <!-- opacity tools -->
            <IconButton
                class="top-tool-icon-button left-border-radius"
                {iconSize}
                tooltip="{tr.editingImageOcclusionToggleTranslucent()} ({getPlatformString(
                    toggleTranslucentKeyCombination,
                )})"
                on:click={() => {
                    maskOpacity = !maskOpacity;
                    makeMaskTransparent(canvas, maskOpacity);
                }}
            >
                <Icon icon={mdiEye} />
            </IconButton>
            {#if $ioMaskEditorVisible && !$textEditingState}
                <Shortcut
                    keyCombination={toggleTranslucentKeyCombination}
                    on:action={() => {
                        maskOpacity = !maskOpacity;
                        makeMaskTransparent(canvas, maskOpacity);
                    }}
                />
            {/if}

            <!-- cursor tools -->
            {#each deleteDuplicateTools as tool}
                <IconButton
                    class="top-tool-icon-button {tool.name === 'duplicate'
                        ? 'right-border-radius'
                        : ''}"
                    {iconSize}
                    tooltip="{tool.tooltip()} ({getPlatformString(tool.shortcut)})"
                    on:click={() => {
                        tool.action(canvas);
                        undoStack.onObjectModified();
                    }}
                >
                    <Icon icon={tool.icon} />
                </IconButton>
                {#if $ioMaskEditorVisible && !$textEditingState}
                    <Shortcut
                        keyCombination={tool.shortcut}
                        on:action={() => {
                            tool.action(canvas);
                            saveNeededStore.set(true);
                        }}
                    />
                {/if}
            {/each}
        </div>

        <div class="tool-button-container">
            <!-- group & ungroup tools -->
            {#each groupUngroupTools as tool}
                <IconButton
                    class="top-tool-icon-button {tool.name === 'group'
                        ? 'left-border-radius'
                        : ''}"
                    {iconSize}
                    tooltip="{tool.tooltip()} ({getPlatformString(tool.shortcut)})"
                    on:click={() => {
                        tool.action(canvas);
                        undoStack.onObjectModified();
                    }}
                >
                    <Icon icon={tool.icon} />
                </IconButton>
                {#if $ioMaskEditorVisible && !$textEditingState}
                    <Shortcut
                        keyCombination={tool.shortcut}
                        on:action={() => {
                            tool.action(canvas);
                            saveNeededStore.set(true);
                        }}
                    />
                {/if}
            {/each}

            <IconButton
                class="top-tool-icon-button dropdown-tool right-border-radius"
                {iconSize}
                tooltip={tr.editingImageOcclusionAlignment()}
                on:click={(e) => {
                    showAlignTools = !showAlignTools;
                    leftPos = e.pageX - 100;
                }}
            >
                <Icon icon={mdiFormatAlignCenter} />
            </IconButton>
        </div>
    </div>

    <div class:show={showAlignTools} class="dropdown-content" style="left:{leftPos}px;">
        {#each alignTools as alignTool}
            <IconButton
                class="top-tool-icon-button"
                {iconSize}
                tooltip="{alignTool.tooltip()} ({getPlatformString(
                    alignTool.shortcut,
                )})"
                on:click={() => {
                    alignTool.action(canvas);
                    undoStack.onObjectModified();
                }}
            >
                <Icon icon={alignTool.icon} />
            </IconButton>
            {#if $ioMaskEditorVisible && !$textEditingState}
                <Shortcut
                    keyCombination={alignTool.shortcut}
                    on:action={() => {
                        alignTool.action(canvas);
                    }}
                />
            {/if}
        {/each}
    </div>
</div>

<style>
    .top-tool-bar-container {
        display: flex;
        overflow-y: scroll;
        z-index: 99;
        margin-left: 106px;
        margin-top: 2px;
    }

    :global([dir="rtl"] .top-tool-bar-container) {
        margin-left: unset;
        margin-right: 28px;
    }

    .undo-redo-button {
        margin-right: 2px;
        display: flex;
    }

    .tool-button-container {
        margin-left: 2px;
        margin-right: 2px;
        display: flex;
    }

    :global(.left-border-radius) {
        border-radius: 5px 0 0 5px !important;
    }

    :global([dir="rtl"] .left-border-radius) {
        border-radius: 0 5px 5px 0 !important;
    }

    :global(.right-border-radius) {
        border-radius: 0 5px 5px 0 !important;
    }

    :global([dir="rtl"] .right-border-radius) {
        border-radius: 5px 0 0 5px !important;
    }

    :global(.border-radius) {
        border-radius: 5px !important;
    }

    :global(.top-tool-icon-button) {
        border: unset;
        display: inline;
        width: 32px;
        height: 32px;
        margin: unset;
        padding: 6px !important;
        font-size: 16px !important;
    }

    :global(.top-tool-icon-button:active) {
        background: var(--highlight-bg) !important;
    }

    .dropdown-content {
        display: none;
        position: absolute;
        z-index: 100;
        top: 40px;
        margin-top: 1px;
    }

    .show {
        display: table;
    }

    ::-webkit-scrollbar {
        width: 0.1em !important;
        height: 0.1em !important;
    }

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

    :global([dir="rtl"] .tool-bar-container) {
        left: unset;
        right: 2px;
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

    :global(.icon-border-radius) {
        border-radius: 5px !important;
    }

    :global(.dropdown-tool-mode) {
        height: 38px !important;
        display: inline;
    }
</style>
