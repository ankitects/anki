<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { createEventDispatcher, tick } from "svelte";
    import { writable } from "svelte/store";

    import HorizontalResizer from "./HorizontalResizer.svelte";
    import Pane from "./Pane.svelte";
    import PaneContent from "./PaneContent.svelte";
    import PaneHeader from "./PaneHeader.svelte";
    import { resizable } from "./resizable";
    import type { PaneInput } from "./utils";
    import { filterInPlace } from "./utils";
    import VerticalSplit from "./VerticalSplit.svelte";

    type T = any;

    /**
     * Should be non-empty.
     */
    export let panes: PaneInput<T>[];
    export let baseSize = 600;
    export let root = false;

    $: closeable = !root || panes.length > 1;

    const paneComponents: (Pane | VerticalSplit)[] = [];

    $: filterInPlace(paneComponents);

    const dispatch = createEventDispatcher();

    async function closePane(index: number): Promise<void> {
        panes.splice(index, 1);
        paneComponents.splice(index, 1);

        /* triggers update */
        panes = panes;

        if (panes.length === 1) {
            await tick();
            dispatch("paneinline");
        }
    }

    function inlinePane(index: number): void {
        panes.splice(index, 1, panes[index].data[0]);
        paneComponents.splice(index, 1);

        /* triggers update */
        panes = panes;
    }

    const resizes = writable(false);
    const paneSize = writable(baseSize);

    const [
        { resizesDimension: resizesWidth, resizedDimension: resizedWidth },
        action,
        resizer,
    ] = resizable(baseSize, resizes, paneSize);
    export { resizer as width };

    let clientHeight: number;
</script>

<div
    bind:clientHeight
    class="horizontal-split"
    class:resize={$resizes}
    class:resize-width={$resizesWidth}
    style:--pane-size={$paneSize}
    style:--resized-width="{$resizedWidth}px"
    use:action={(element) => element.offsetWidth}
>
    {#each panes as { id, data }, index (id)}
        {#if index > 0}
            <HorizontalResizer
                components={paneComponents}
                index={index - 1}
                {clientHeight}
            />
        {/if}

        {#if Array.isArray(data)}
            <VerticalSplit
                bind:this={paneComponents[index]}
                panes={data}
                let:id={innerId}
                let:data={innerData}
                on:paneinline={() => inlinePane(index)}
                on:panefocus
                on:panehsplit
                on:panevsplit
            >
                <slot name="header" slot="header" id={innerId} data={innerData} />
                <slot name="content" slot="content" id={innerId} data={innerData} />
            </VerticalSplit>
        {:else}
            <Pane
                bind:this={paneComponents[index]}
                on:focusin={() => dispatch("panefocus", id)}
                on:pointerdown={() => dispatch("panefocus", id)}
            >
                <PaneHeader
                    {closeable}
                    on:close={() => closePane(index)}
                    on:hsplit={() => dispatch("panehsplit", id)}
                    on:vsplit={() => dispatch("panevsplit", id)}
                >
                    <slot name="header" {id} {data} />
                </PaneHeader>
                <PaneContent>
                    <slot name="content" {id} {data} />
                </PaneContent>
            </Pane>
        {/if}
    {/each}
</div>

<style lang="scss">
    @use "./panes" as panes;

    .horizontal-split {
        @include panes.resizable(column, true, false);
    }
</style>
