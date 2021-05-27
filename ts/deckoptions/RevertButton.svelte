<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "lib/i18n";
    import { revertIcon } from "./icons";
    import { createEventDispatcher } from "svelte";
    import { isEqual as isEqualLodash, cloneDeep } from "lodash-es";
    // import { onMount } from "svelte";
    // import Tooltip from "bootstrap/js/dist/tooltip";

    let ref: HTMLDivElement;

    // fixme: figure out why this breaks halfway down the page
    // onMount(() => {
    //     new Tooltip(ref, {
    //         placement: "bottom",
    //         html: true,
    //         offset: [0, 20],
    //     });
    // });

    export let value: any;
    export let defaultValue: any;

    const dispatch = createEventDispatcher();

    function isEqual(a: unknown, b: unknown): boolean {
        if (typeof a === "number" && typeof b === "number") {
            // round to .01 precision before comparing,
            // so the values coming out of the UI match
            // the originals
            return isEqualLodash(Math.round(a * 100) / 100, Math.round(b * 100) / 100);
        } else {
            return isEqualLodash(a, b);
        }
    }

    let modified: boolean;
    $: modified = !isEqual(value, defaultValue);

    /// This component can be used either with bind:value, or by listening
    /// to the revert event.
    function revert(): void {
        value = cloneDeep(defaultValue);
        dispatch("revert", { value });
    }
</script>

<span
    bind:this={ref}
    class="badge"
    class:invisible={!modified}
    title={tr.deckConfigRevertButtonTooltip()}
    on:click={revert}
>
    {@html revertIcon}
</span>

<style lang="scss">
    span :global(svg) {
        vertical-align: -0.125rem;
        opacity: 0.3;
    }
</style>
