<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script context="module" lang="ts">
    import type { Writable } from "svelte/store";
    import { LRUCache } from "lru-cache";

    const imageToHeightMap = new Map<string, Writable<number>>();
    const observer = new ResizeObserver((entries: ResizeObserverEntry[]) => {
        for (const entry of entries) {
            const image = entry.target as HTMLImageElement;
            const store = imageToHeightMap.get(image.dataset.uuid!)!;
            store.set(entry.contentRect.height);

            setTimeout(() => entry.target.dispatchEvent(new Event("resize")));
        }
    });

    type Cache = LRUCache<string, [string, string]>;

    const caches: { [key: string]: Cache } = {};

    function getCache(...keyParts: any) {
        const key = keyParts.toString(); // primitive parts or arrays only
        if (!(key in caches)) {
            caches[key] = new LRUCache({ max: 10 });
        }
        return caches[key];
    }
</script>

<script lang="ts">
    import { randomUUID } from "@tslib/uuid";
    import { onDestroy } from "svelte";
    import { writable } from "svelte/store";

    import { pageTheme } from "$lib/sveltelib/theme";

    import { convertMathjax, emptyIcon, unescapeSomeEntities } from "./mathjax";
    import { CooldownTimer } from "./cooldown-timer";

    export let mathjax: string;
    export let block: boolean;
    export let fontSize: number;

    // MathJax is loaded on demand, so the first typeset in a session resolves
    // asynchronously. Show the same icon an empty element uses until it does.
    let [converted, title]: [string, string] = emptyIcon($pageTheme.isDark, fontSize);

    const debouncer = new CooldownTimer(500);

    // Bumped for every conversion, so that a typeset which resolves after a
    // newer one was scheduled cannot overwrite the newer result.
    let generation = 0;

    $: debouncer.schedule(() => {
        const cache = getCache($pageTheme.isDark, fontSize);
        const cached = cache.get(mathjax);
        if (cached) {
            generation++;
            [converted, title] = cached;
            return;
        }

        const current = ++generation;
        const input = mathjax;

        convertMathjax(
            unescapeSomeEntities(input),
            $pageTheme.isDark,
            fontSize,
        ).then((entry) => {
            cache.set(input, entry);
            if (current === generation) {
                [converted, title] = entry;
            }
        });
    });
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
    @import "./mathjax.scss";
</style>
