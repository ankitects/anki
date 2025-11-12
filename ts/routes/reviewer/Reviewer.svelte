<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { isNightMode, type ReviewerState } from "./reviewer";

    let iframe: HTMLIFrameElement;
    export let state: ReviewerState;

    $: if (iframe) {
        state.registerIFrame(iframe);
        state.registerShortcuts();
    }
    $: tooltipMessage = state.tooltipMessage;
</script>

<div class="iframe-container">
    <iframe
        src={"/_anki/pages/reviewer-inner.html" + (isNightMode() ? "?nightMode" : "")}
        bind:this={iframe}
        title="card"
        frameborder="0"
        sandbox="allow-scripts"
    ></iframe>

    <div class="tooltip" style:opacity={$tooltipMessage ? 1 : 0}>
        {$tooltipMessage}
    </div>
</div>

<style lang="scss">
    div.iframe-container {
        position: relative;
        flex: 1;
    }

    div.tooltip {
        position: absolute;
        left: 0;
        bottom: 0;
        padding: 0.8em;
        background-color: var(--bs-tooltip-color);
        z-index: var(--bs-tooltip-z-index);
        border: 2px solid white;
        opacity: 1;
    }

    iframe {
        width: 100%;
        height: 100%;
        visibility: hidden;
    }
</style>
