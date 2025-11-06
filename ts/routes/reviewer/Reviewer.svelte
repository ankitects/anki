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

    const innerPort = new URLSearchParams(window.location.search).get("p");

    $: hostname = innerPort
        ? `${window.location.protocol}//${window.location.hostname}:${innerPort}/reviewer-inner.html`
        : "/_anki/pages/reviewer-inner.html"; // fallback

    $: sandboxAllowList =
        "allow-scripts" +
        (new URL(hostname).origin != window.location.origin
            ? " allow-same-origin"
            : "");
</script>

<div id="qa">
    <iframe
        src={hostname + (isNightMode() ? "?nightMode" : "")}
        bind:this={iframe}
        title="card"
        frameborder="0"
        sandbox={sandboxAllowList}
    ></iframe>
</div>

<style lang="scss">
    #qa {
        flex: 1;
    }

    iframe {
        width: 100%;
        height: 100%;
        visibility: hidden;
    }
</style>
