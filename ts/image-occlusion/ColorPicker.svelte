<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->

<script lang="ts">
    import IconButton from "components/IconButton.svelte";

    import { mdiCircle, mdiSquare } from "./icons";
    import { lineToolConfig, pathToolConfig } from "./store";

    const defaultColor = ["ffffff", "000000", "ff0000", "0000ff", "00ff00"];
    const defaultHighlighterColor = [
        "ffffff20",
        "00000020",
        "ff000020",
        "0000ff20",
        "00ff0020",
    ];
    const defaultSize = [0.1, 0.2, 0.4, 0.8, 1.4];

    export let show = false;
    export let iconSize;
    export let top;
    export let activeAnnotationTool = "draw-path";
</script>

<div class="color-picker" style="top: {top}px; display: {show ? '' : 'none'}">
    <div>
        {#each defaultColor as color, index}
            <IconButton
                style={`color: #${color} !important`}
                class="{index == 0 ? 'border-radius-top-left' : ''}
                    {index == 4 ? 'border-radius-top-right' : ''}"
                on:click={() => {
                    if (activeAnnotationTool === "draw-path") {
                        $pathToolConfig.color = `#${color}`;
                    }
                    if (activeAnnotationTool === "draw-line") {
                        $lineToolConfig.color = `#${color}`;
                    }
                }}
            >
                {@html mdiSquare}
            </IconButton>
        {/each}
    </div>
    <div>
        {#each defaultHighlighterColor as color}
            <IconButton
                style={`color: #${color} !important`}
                on:click={() => {
                    if (activeAnnotationTool === "draw-path") {
                        $pathToolConfig.color = `#${color}`;
                    }
                    if (activeAnnotationTool === "draw-line") {
                        $lineToolConfig.color = `#${color}`;
                    }
                }}
            >
                {@html mdiSquare}
            </IconButton>
        {/each}
    </div>
    <div>
        {#each defaultSize as size, index}
            <IconButton
                class="{index == 0 ? 'border-radius-bottom-left' : ''}
            {index == 4 ? 'border-radius-bottom-right' : ''}"
                iconSize={iconSize * size}
                on:click={() => {
                    if (activeAnnotationTool === "draw-path") {
                        $pathToolConfig.size = size * 20;
                    }
                    if (activeAnnotationTool === "draw-line") {
                        $lineToolConfig.size = size * 20;
                    }
                }}
            >
                {@html mdiCircle}
            </IconButton>
        {/each}
    </div>
</div>

<style>
    .color-picker {
        width: fit-content;
        z-index: 9999;
        position: absolute;
        left: 42px;
    }

    :global(.border-radius-top-left) {
        border-top-left-radius: 5px !important;
    }

    :global(.border-radius-bottom-left) {
        border-bottom-left-radius: 5px !important;
    }

    :global(.border-radius-top-right) {
        border-top-right-radius: 5px !important;
    }

    :global(.border-radius-bottom-right) {
        border-bottom-right-radius: 5px !important;
    }

    :global(.border-hint-ffffff) {
        border-bottom: 3px solid #ffffff !important;
    }

    :global(.border-hint-000000) {
        border-bottom: 3px solid #000000 !important;
    }

    :global(.border-hint-ff0000) {
        border-bottom: 3px solid #ff0000 !important;
    }

    :global(.border-hint-0000ff) {
        border-bottom: 3px solid #0000ff !important;
    }

    :global(.border-hint-00ff00) {
        border-bottom: 3px solid #00ff00 !important;
    }

    :global(.border-hint-00000020) {
        border-bottom: 3px solid #6e6e6e !important;
    }

    :global(.border-hint-ffffff20) {
        border-bottom: 3px solid #e5e5e5 !important;
    }

    :global(.border-hint-ff000020) {
        border-bottom: 3px solid #ffcaca !important;
    }

    :global(.border-hint-0000ff20) {
        border-bottom: 3px solid #adadff !important;
    }

    :global(.border-hint-00ff0020) {
        border-bottom: 3px solid #a1ffa1 !important;
    }
</style>
