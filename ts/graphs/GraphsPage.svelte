<script lang="typescript">
    import "../sass/core.css";

    import type { SvelteComponent } from "svelte/internal";
    import type { I18n } from "anki/i18n";

    import WithGraphData from "./WithGraphData.svelte";
    import BrowserSearch from "./BrowserSearch.svelte";

    export let i18n: I18n;
    export let nightMode: boolean;
    export let graphs: SvelteComponent[];

    export let initialSearch: string;
    export let initialDays: number;
    export let controller: SvelteComponent | null;

    let query: string;

    const activateBrowserSearch = (event: CustomEvent): void => {
        query = event.detail.query;
    };
</script>

<style lang="scss">
    div {
        @media only screen and (max-width: 600px) {
            font-size: 12px;
        }
    }
</style>

<div>
    <WithGraphData
        {initialSearch}
        {initialDays}
        let:search
        let:days
        let:loading
        let:sourceData
        let:preferences
        let:revlogRange>
        {#if controller}
            <svelte:component this={controller} {i18n} {search} {days} {loading} />
        {/if}

        {#if sourceData && preferences && revlogRange}
            {#each graphs as graph}
                <svelte:component
                    this={graph}
                    {sourceData}
                    {preferences}
                    {revlogRange}
                    {i18n}
                    {nightMode}
                    on:search={activateBrowserSearch} />
            {/each}
        {/if}

        <BrowserSearch {search} {query} />
    </WithGraphData>
</div>
