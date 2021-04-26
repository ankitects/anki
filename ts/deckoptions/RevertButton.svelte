<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "lib/i18n";
    import { revertIcon } from "./icons";
    import { createEventDispatcher } from "svelte";
    import { isEqual, cloneDeep } from "lodash-es";
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

    let modified: boolean;
    $: modified = !isEqual(value, defaultValue);

    /// This component can be used either with bind:value, or by listening
    /// to the revert event.
    function revert(): void {
        value = cloneDeep(defaultValue);
        dispatch("revert", { value });
    }
</script>

<style lang="scss">
    .img-div {
        display: flex;

        :global(svg) {
            align-self: center;
            opacity: 0.3;
        }
    }
</style>

{#if modified}
    <div
        class="img-div"
        on:click={revert}
        bind:this={ref}
        title={tr.deckConfigRevertButtonTooltip()}>
        {@html revertIcon}
    </div>
{/if}
