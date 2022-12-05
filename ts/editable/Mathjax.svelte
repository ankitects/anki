<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script context="module" lang="ts">
    import type { Writable } from "svelte/store";

    const imageToHeightMap = new Map<string, Writable<number>>();
    const observer = new ResizeObserver((entries: ResizeObserverEntry[]) => {
        for (const entry of entries) {
            const image = entry.target as HTMLImageElement;
            const store = imageToHeightMap.get(image.dataset.uuid!)!;
            store.set(entry.contentRect.height);

            setTimeout(() => entry.target.dispatchEvent(new Event("resize")));
        }
    });
</script>

<script lang="ts">
    import { randomUUID } from "@tslib/uuid";
    import { onDestroy } from "svelte";
    import { writable } from "svelte/store";

    import { pageTheme } from "../sveltelib/theme";
    import { convertMathjax, unescapeSomeEntities } from "./mathjax";

    export let mathjax: string;
    export let block: boolean;
    export let fontSize: number;

    $: [converted, title] = convertMathjax(
        unescapeSomeEntities(mathjax),
        $pageTheme.isDark,
        fontSize,
    );
    $: empty = title === "MathJax";
    $: encoded = encodeURIComponent(converted);

    const uuid = randomUUID();
    const imageHeight = writable(0);
    imageToHeightMap.set(uuid, imageHeight);

    $: verticalCenter = -$imageHeight / 2 + fontSize / 4;

    let image: HTMLImageElement;

    export function moveCaretAfter(position?: [number, number]): void {
        // This should trigger a focusing of the Mathjax Handle
        image.dispatchEvent(
            new CustomEvent("movecaretafter", {
                detail: { image, position },
                bubbles: true,
                composed: true,
            }),
        );
    }

    export function selectAll(): void {
        image.dispatchEvent(
            new CustomEvent("selectall", {
                detail: image,
                bubbles: true,
                composed: true,
            }),
        );
    }

    function observe(image: Element) {
        observer.observe(image);

        return {
            destroy() {
                observer.unobserve(image);
            },
        };
    }

    onDestroy(() => imageToHeightMap.delete(uuid));
</script>

<img
    bind:this={image}
    src="data:image/svg+xml,{encoded}"
    class:block
    class:empty
    class="mathjax"
    style:--vertical-center="{verticalCenter}px"
    style:--font-size="{fontSize}px"
    alt="Mathjax"
    {title}
    data-anki="mathjax"
    data-uuid={uuid}
    on:dragstart|preventDefault
    use:observe
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
        margin: 1rem auto;
        transform: scale(1.1);
    }

    .empty {
        vertical-align: text-bottom;

        width: var(--font-size);
        height: var(--font-size);
    }
</style>
