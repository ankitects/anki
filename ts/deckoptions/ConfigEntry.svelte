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

<div {id} class="outer">
    {#if label}
        <div class="table">
            <span class="vcenter">
                {label}
                {#if renderedTooltip}
                    <HelpPopup html={renderedTooltip} />
                {/if}
            </span>
        </div>
    {/if}

    <div class="input-grid" class:full-grid-width={wholeLine}>
        <slot />
        <RevertButton bind:value {defaultValue} on:revert />
    </div>

    <div class="full-grid-width">
        {#each warnings as warning}
            {#if warning}
                <div class="alert alert-warning" in:slide out:slide>{warning}</div>
            {/if}
        {/each}
    </div>
</div>

<style lang="scss">
    .outer {
        display: grid;
        grid-template-columns: 7fr 3fr;
        grid-row-gap: 0.5em;
    }

    .full-grid-width {
        grid-column: 1 / 6;
    }

    .table {
        display: table;
        height: 100%;
    }

    .vcenter {
        display: table-cell;
        vertical-align: middle;
    }

    .alert {
        margin-top: 0.5em;
    }

    .input-grid {
        display: grid;
        grid-column-gap: 0.5em;
        grid-template-columns: 10fr 16px;
    }
</style>
