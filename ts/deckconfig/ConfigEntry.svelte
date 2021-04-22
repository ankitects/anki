<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import RevertIcon from "./RevertIcon.svelte";
    import HelpPopup from "./HelpPopup.svelte";

    export let label: string;
    export let subLabel = "";
    export let value: any;
    export let defaultValue: any;
    /// empty strings will be ignored
    export let warnings: string[] = [];
</script>

<style lang="scss">
    .outer {
        margin-top: 1em;

        display: grid;
        grid-template-columns: 1fr 1fr;
        grid-column-gap: 0.5em;
    }

    .full-grid-width {
        grid-row-start: 2;
        grid-column: 1 / 3;
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
        // width: 90vw;
        display: grid;
        grid-column-gap: 0.5em;
        grid-template-columns: 10fr 1fr;
    }
</style>

<div class="outer">
    <div class="table">
        <span class="vcenter">
            {label}
            {#if subLabel}
                <HelpPopup html={subLabel} />
            {/if}
        </span>
    </div>

    <div class="input-grid">
        <slot />
        <RevertIcon bind:value {defaultValue} on:revert />
    </div>

    <div class="full-grid-width">
        {#each warnings as warning}
            {#if warning}
                <div class="alert alert-warning">{warning}</div>
            {/if}
        {/each}
    </div>
</div>
