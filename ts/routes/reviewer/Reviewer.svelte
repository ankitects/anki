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
</script>

<div id="qa">
    <iframe
        src={"/_anki/pages/reviewer-inner.html" + (isNightMode() ? "?nightMode" : "")}
        bind:this={iframe}
        title="card"
        frameborder="0"
        sandbox="allow-scripts"
    ></iframe>
</div>

<style lang="scss">
    #qa {
        flex: 1;
    }

    iframe {
        width: 100%;
        height: 100%;
    }
</style>
