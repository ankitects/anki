<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script>
    import IconButton from "../components/IconButton.svelte";
    import { mdiEye, mdiFormatAlignCenter } from "./icons";
    import { makeMaskTransparent } from "./tools/lib";
    import {
        alignTools,
        deleteDuplicateTools,
        groupUngroupTools,
        zoomTools,
    } from "./tools/more-tools";
    import { undoRedoTools } from "./tools/tool-undo-redo";

    export let canvas;
    export let instance;
    export let iconSize;
    let showAlignTools = false;
    let leftPos = 82;
    let maksOpacity = false;

    document.addEventListener("click", (event) => {
        const upperCanvas = document.querySelector(".upper-canvas");
        if (event.target == upperCanvas) {
            showAlignTools = false;
        }
    });
</script>

<div class="top-tool-bar-container">
    <!-- undo & redo tools -->
    <div class="undo-redo-button">
        {#each undoRedoTools as tool}
            <IconButton
                class="top-tool-icon-button {tool.name === 'undo'
                    ? 'left-border-radius'
                    : 'right-border-radius'}"
                {iconSize}
                on:click={() => {
                    tool.action(canvas);
                }}
            >
                {@html tool.icon}
            </IconButton>
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
                on:click={() => {
                    tool.action(instance);
                }}
            >
                {@html tool.icon}
            </IconButton>
        {/each}
    </div>

    <div class="tool-button-container">
        <!-- opacity tools -->
        <IconButton
            class="top-tool-icon-button left-border-radius"
            {iconSize}
            on:click={() => {
                maksOpacity = !maksOpacity;
                makeMaskTransparent(canvas, maksOpacity);
            }}
        >
            {@html mdiEye}
        </IconButton>

        <!-- cursor tools -->
        {#each deleteDuplicateTools as tool}
            <IconButton
                class="top-tool-icon-button {tool.name === 'duplicate'
                    ? 'right-border-radius'
                    : ''}"
                {iconSize}
                on:click={() => {
                    tool.action(canvas);
                }}
            >
                {@html tool.icon}
            </IconButton>
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
                on:click={() => {
                    tool.action(canvas);
                }}
            >
                {@html tool.icon}
            </IconButton>
        {/each}

        <IconButton
            class="top-tool-icon-button dropdown-tool right-border-radius"
            {iconSize}
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
            on:click={() => {
                alignTool.action(canvas);
            }}
        >
            {@html alignTool.icon}
        </IconButton>
    {/each}
</div>

<style>
    .top-tool-bar-container {
        display: flex;
        overflow-y: scroll;
        z-index: 99;
        margin-left: 98px;
        margin-top: 2px;
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

    :global(.right-border-radius) {
        border-radius: 0 5px 5px 0 !important;
    }

    :global(.top-tool-icon-button) {
        border: unset;
        display: inline;
        width: 32px;
        height: 32px;
        margin: unset;
        padding: 6px !important;
    }

    .dropdown-content {
        display: none;
        position: absolute;
        z-index: 100;
        top: 40px;
        margin-top: 1px;
    }

    .show {
        display: block;
    }

    ::-webkit-scrollbar {
        width: 0.1em !important;
        height: 0.1em !important;
    }
</style>
