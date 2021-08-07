<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { onMount, getContext } from "svelte";
    import { nightModeKey } from "components/context-keys";
    import { convertMathjax } from "./mathjax";

    export let mathjax: string;
    export let block: boolean;
    export let autofocus = false;

    /* have fixed fontSize for normal */
    export const fontSize: number = 20;

    const nightMode = getContext<boolean>(nightModeKey);

    $: [converted, title] = convertMathjax(mathjax, nightMode, fontSize);
    $: empty = title === "MathJax";
    $: encoded = encodeURIComponent(converted);

    let image: HTMLImageElement;

    onMount(() => {
        if (autofocus) {
            image.click();
        }
    });
</script>

<img
    bind:this={image}
    src="data:image/svg+xml,{encoded}"
    class:block
    class:empty
    alt="Mathjax"
    {title}
    data-anki="mathjax"
    on:dragstart|preventDefault
/>

<style lang="scss">
    .block {
        display: block;
        margin: auto;
    }

    .empty {
        vertical-align: sub;
    }
</style>
