<script lang="ts">
    import IconButton from "components/IconButton.svelte";

    import { mdiCircle, mdiSquare } from "./icons";

    const defaultColor = ["ffffff", "000000", "ff0000", "0000ff", "00ff00"];
    const defaultHighlighterColor = [
        "ffffff20",
        "00000020",
        "ff000020",
        "0000ff20",
        "00ff0020",
    ];
    const defaultSize = [0.1, 0.2, 0.4, 0.8, 1.6];

    export let show = false;
    export let iconSize;
    export let top;
    export let activeAnnotationTool = "draw-path";
</script>

<div class="color-picker" style="top: {top}px; display: {show ? '' : 'none'}">
    <div>
        {#each defaultColor as color, index}
            <IconButton
                class="color-picker-{color} {index == 0 ? 'border-radius-top-left' : ''}
                    {index == 2 ? 'border-radius-top-right' : ''}"
                on:click={() => {
                    localStorage.setItem(`${activeAnnotationTool}-color`, `#${color}`);
                }}
            >
                {@html mdiSquare}
            </IconButton>
        {/each}
    </div>
    <div>
        {#each defaultHighlighterColor as color}
            <IconButton
                class="color-picker-{color}"
                on:click={() => {
                    localStorage.setItem(`${activeAnnotationTool}-color`, `#${color}`);
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
            {index == 2 ? 'border-radius-bottom-right' : ''}"
                iconSize={iconSize * size}
                on:click={() => {
                    localStorage.setItem(
                        `${activeAnnotationTool}-size`,
                        size.toString(),
                    );
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

    :global(.color-picker-ffffff) {
        color: #ffffff !important;
    }

    :global(.color-picker-000000) {
        color: #000000 !important;
    }

    :global(.color-picker-ff0000) {
        color: #ff0000 !important;
    }

    :global(.color-picker-0000ff) {
        color: #0000ff !important;
    }

    :global(.color-picker-00ff00) {
        color: #00ff00 !important;
    }

    :global(.color-picker-00000020) {
        color: #000000 !important;
    }

    :global(.color-picker-ffffff20) {
        color: #ffffff20 !important;
    }

    :global(.color-picker-ff000020) {
        color: #ff000020 !important;
    }

    :global(.color-picker-0000ff20) {
        color: #0000ff20 !important;
    }

    :global(.color-picker-00ff0020) {
        color: #00ff0020 !important;
    }

    :global(.border-radius-top-left) {
        border-top-left-radius: 5px;
    }

    :global(.border-radius-bottom-left) {
        border-bottom-left-radius: 5px;
    }

    :global(.border-radius-top-right) {
        border-top-right-radius: 5px;
    }

    :global(.border-radius-bottom-right) {
        border-bottom-right-radius: 5px;
    }
</style>
