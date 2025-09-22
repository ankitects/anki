<script lang="ts">
    import { bridgeCommand } from "@tslib/bridgecommand";
    import type { Writable } from "svelte/store";
    import { isNightMode } from "../../html-filter/helpers";

    export let html: Writable<string>;
    export let cardClass: Writable<string>;

    let iframe: HTMLIFrameElement;

    html.subscribe(($html) => {
        if (iframe?.contentDocument) {
            iframe.contentDocument.body.innerHTML = $html;
            iframe.contentDocument.head.innerHTML = document.head.innerHTML;
            iframe.contentDocument.body.className = isNightMode()
                ? "nightMode card"
                : "card";
            iframe.contentDocument.querySelector("html")!.className = isNightMode()
                ? "night-mode"
                : "";
            //@ts-ignore
            iframe.contentDocument.pycmd = bridgeCommand;
        }
    });
</script>

<div id="qa" class={$cardClass}>
    <iframe bind:this={iframe} title="card" frameborder="0"></iframe>
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
