<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { onMount, onDestroy, tick } from "svelte";
    import { pageTheme } from "../sveltelib/theme";
    import { convertMathjax } from "./mathjax";

    export let mathjax: string;
    export let block: boolean;
    export let autofocus = false;

    /* have fixed fontSize for normal */
    export const fontSize: number = 20;

    $: [converted, title] = convertMathjax(mathjax, $pageTheme.isDark, fontSize);
    $: empty = title === "MathJax";

    let encoded: string;
    let imageHeight: number;

    $: encoded = encodeURIComponent(converted);

    let image: HTMLImageElement;

    const observer = new ResizeObserver(async () => {
        imageHeight = image.getBoundingClientRect().height;
        await tick();
        setTimeout(() => image.dispatchEvent(new Event("resize")));
    });

    onMount(() => {
        observer.observe(image);

        if (autofocus) {
            // This should trigger a focusing of the Mathjax Handle
            const focusEvent = new CustomEvent("focusmathjax", {
                detail: image,
                bubbles: true,
                composed: true,
            });

            image.dispatchEvent(focusEvent);
        }
    });

    onDestroy(() => {
        observer.unobserve(image);
        observer.disconnect();
    });
</script>

<img
    bind:this={image}
    src="data:image/svg+xml,{encoded}"
    class:block
    class:empty
    style="--vertical-center: {-imageHeight / 2 + fontSize / 4}px;"
    alt="Mathjax"
    {title}
    data-anki="mathjax"
    on:dragstart|preventDefault
/>

<style lang="scss">
    :global(anki-mathjax) {
        white-space: pre;
    }

    img {
        vertical-align: var(--vertical-center);
    }

    .block {
        display: block;
        margin: auto;
    }

    .empty {
        vertical-align: sub;
    }
</style>
