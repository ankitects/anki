<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { directionKey } from "@tslib/context-keys";
    import * as tr from "@tslib/ftl";
    import { getPlatformString } from "@tslib/shortcuts";
    import DropdownItem from "components/DropdownItem.svelte";
    import IconButton from "components/IconButton.svelte";
    import Popover from "components/Popover.svelte";
    import Shortcut from "components/Shortcut.svelte";
    import WithFloating from "components/WithFloating.svelte";
    import { getContext, onMount } from "svelte";
    import type { Readable } from "svelte/store";

    import { mdiEye, mdiFormatAlignCenter, mdiSquare, mdiViewDashboard } from "./icons";
    import { emitChangeSignal } from "./MaskEditor.svelte";
    import { hideAllGuessOne, ioMaskEditorVisible, textEditingState } from "./store";
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
    import { removeUnfinishedPolygon } from "./tools/tool-polygon";
    import { undoRedoTools, undoStack } from "./tools/tool-undo-redo";

    export let canvas;
    export let instance;
    export let iconSize;
    export let activeTool = "cursor";
    let showAlignTools = false;
    let leftPos = 82;
    let maksOpacity = false;
    let showFloating = false;
    const direction = getContext<Readable<"ltr" | "rtl">>(directionKey);

    document.addEventListener("click", (event) => {
        const upperCanvas = document.querySelector(".upper-canvas");
        if (event.target == upperCanvas) {
            showAlignTools = false;
        }
    });

    // handle zoom event when mouse scroll and ctrl key are hold for panzoom
    let clicked = false;
    let dbclicked = false;
    let move = false;
    let wheel = false;

    onMount(() => {
        window.addEventListener("mousedown", (event) => {
            if (event.ctrlKey) {
                clicked = true;
            }
        });
        window.addEventListener("mouseup", (event) => {
            if (event.ctrlKey) {
                clicked = false;
            }
        });
        window.addEventListener("mousemove", (event) => {
            if (event.ctrlKey) {
                move = true;
            }
        });
        window.addEventListener("wheel", (event) => {
            if (event.ctrlKey) {
                wheel = true;
            }
        });
        window.addEventListener("dblclick", (event) => {
            if (event.ctrlKey) {
                dbclicked = true;
            }
        });
        window.addEventListener("keyup", (event) => {
            if (event.key == "Control") {
                clicked = false;
                move = false;
                wheel = false;
                dbclicked = false;
            }
        });
        window.addEventListener("keydown", (event) => {
            if (event.key == "Control") {
                clicked = false;
                move = false;
                wheel = false;
                dbclicked = false;
            }
        });
        window.addEventListener("keydown", (event) => {
            if (event.key == "Control" && activeTool != "magnify") {
                instance.resume();
            }
        });
        window.addEventListener("keyup", (event) => {
            if (event.key == "Control" && activeTool != "magnify") {
                instance.pause();
            }
        });
        window.addEventListener("wheel", () => {
            if (clicked && move && wheel && !dbclicked) {
                enableMagnify();
            }
        });
    });

    // handle tool changes after initialization
    $: if (instance && canvas) {
        disableFunctions();
        enableSelectable(canvas, true);
        // remove unfinished polygon when switching to other tools
        removeUnfinishedPolygon(canvas);

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
            case "draw-text":
                drawText(canvas);
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

    function changeOcclusionType(occlusionType: "all" | "one"): void {
        $hideAllGuessOne = occlusionType === "all";
        emitChangeSignal();
    }
    const enableMagnify = () => {
        disableFunctions();
        enableSelectable(canvas, false);
        instance.resume();
        activeTool = "magnify";
    };
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
            }}
        >
            {@html tool.icon}
        </IconButton>
        {#if $ioMaskEditorVisible && !$textEditingState}
            <Shortcut
                keyCombination={tool.shortcut}
                on:action={() => {
                    activeTool = tool.id;
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
                {@html $hideAllGuessOne ? mdiViewDashboard : mdiSquare}
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
                    {@html tool.icon}
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
                        tool.action(instance);
                    }}
                >
                    {@html tool.icon}
                </IconButton>
                {#if $ioMaskEditorVisible && !$textEditingState}
                    <Shortcut
                        keyCombination={tool.shortcut}
                        on:action={() => {
                            tool.action(instance);
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
                    maksOpacity = !maksOpacity;
                    makeMaskTransparent(canvas, maksOpacity);
                }}
            >
                {@html mdiEye}
            </IconButton>
            {#if $ioMaskEditorVisible && !$textEditingState}
                <Shortcut
                    keyCombination={toggleTranslucentKeyCombination}
                    on:action={() => {
                        maksOpacity = !maksOpacity;
                        makeMaskTransparent(canvas, maksOpacity);
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
                    {@html tool.icon}
                </IconButton>
                {#if $ioMaskEditorVisible && !$textEditingState}
                    <Shortcut
                        keyCombination={tool.shortcut}
                        on:action={() => {
                            tool.action(canvas);
                            emitChangeSignal();
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
                    {@html tool.icon}
                </IconButton>
                {#if $ioMaskEditorVisible && !$textEditingState}
                    <Shortcut
                        keyCombination={tool.shortcut}
                        on:action={() => {
                            tool.action(canvas);
                            emitChangeSignal();
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
                {@html mdiFormatAlignCenter}
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
                {@html alignTool.icon}
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

    .dropdown-content {
        display: none;
        position: absolute;
        z-index: 100;
        top: 40px;
        margin-top: 1px;
    }

    .show {
        display: flex;
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
