<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { setupReviewer } from "./reviewer";

    let iframe: HTMLIFrameElement;

    $: ({ cardClass } = setupReviewer(iframe));
</script>

<div id="qa" class={$cardClass}>
    <iframe
        src="/_anki/pages/reviewer-inner.html"
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
