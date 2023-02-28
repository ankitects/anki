<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script>
    import IconButton from "../components/IconButton.svelte";
    import { mdiEye, mdiFormatAlignCenter } from "./icons";
    import { makeMaskTransparent } from "./tools/lib";
    import { alignTools, cursorTools, zoomTools } from "./tools/more-tools";
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
        {#each undoRedoTools as undoRedoTool}
            <IconButton
                class="top-tool-icon-button"
                {iconSize}
                on:click={() => {
                    undoRedoTool.action(canvas);
                }}
            >
                {@html undoRedoTool.icon}
            </IconButton>
        {/each}
    </div>

    <!-- zoom tools -->
    {#each zoomTools as zoomBottomTool}
        <IconButton
            class="top-tool-icon-button"
            {iconSize}
            on:click={() => {
                zoomBottomTool.action(instance);
            }}
        >
            {@html zoomBottomTool.icon}
        </IconButton>
    {/each}

    <!-- opacity tools -->
    <IconButton
        class="top-tool-icon-button"
        {iconSize}
        on:click={() => {
            maksOpacity = !maksOpacity;
            makeMaskTransparent(canvas, maksOpacity);
        }}
    >
        {@html mdiEye}
    </IconButton>

    <!-- cursor tools -->
    {#each cursorTools as cursorBottomTool}
        {#if cursorBottomTool.name === "align"}
            <IconButton
                class="top-tool-icon-button dropdown-tool"
                {iconSize}
                on:click={(e) => {
                    showAlignTools = !showAlignTools;
                    leftPos = e.pageX - 100;
                }}
            >
                {@html mdiFormatAlignCenter}
            </IconButton>
        {:else}
            <IconButton
                class="top-tool-icon-button"
                {iconSize}
                on:click={() => {
                    cursorBottomTool.action(canvas);
                }}
            >
                {@html cursorBottomTool.icon}
            </IconButton>
        {/if}
    {/each}
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
        position: fixed;
        top: 46px;
        width: 98%;
        overflow-y: auto;
        z-index: 99;
    }

    .undo-redo-button {
        margin-left: 28px;
        display: flex;
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
        background-color: var(--canvas);
        z-index: 100;
        top: 82px;
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
