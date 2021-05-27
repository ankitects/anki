<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import marked from "marked";
    import { slide } from "svelte/transition";
    import RevertButton from "./RevertButton.svelte";
    import HelpPopup from "./HelpPopup.svelte";

    export let label: string;
    export let tooltip = "";
    export let value: any;
    export let defaultValue: any;
    /// empty strings will be ignored
    export let warnings: string[] = [];
    export let wholeLine = false;
    export let id: string | undefined = undefined;

    let renderedTooltip: string;
    $: renderedTooltip = marked(tooltip);
</script>

<div {id} class="row gx-0 gy-1 mt-0">
    <div class="col-8 d-flex align-items-center">
        {label}{#if renderedTooltip}<HelpPopup html={renderedTooltip} />{/if}
    </div>

    <div class="col-sm-4 d-flex align-items-center">
        <slot />
        <RevertButton bind:value {defaultValue} on:revert />
    </div>
</div>

{#each warnings as warning}
    {#if warning}
        <div class="row gx-0 gy-1 mt-0">
            <div class="col-11 alert alert-warning mb-0" in:slide out:slide>
                {warning}
            </div>
        </div>
    {/if}
{/each}
