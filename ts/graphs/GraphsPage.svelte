<script lang="typescript">
    import "../sass/core.css";

    import type { SvelteComponent } from "svelte/internal";
    import type { I18n } from "anki/i18n";
    import { bridgeCommand } from "anki/bridgecommand";

    import WithGraphData from "./WithGraphData.svelte";

    export let i18n: I18n;
    export let nightMode: boolean;
    export let graphs: SvelteComponent[];

    export let initialSearch: string;
    export let initialDays: number;
    export let controller: SvelteComponent | null;

    const browserSearch = (search: string, query: string) => {
        bridgeCommand(`browserSearch:${search} ${query}`);
    };
</script>

<style lang="scss">
    @media only screen and (max-width: 600px) {
        .base {
            font-size: 12px;
        }
    }
</style>

<div class="base">
    <WithGraphData
        {initialSearch}
        {initialDays}
        let:search
        let:days
        let:pending
        let:loading
        let:sourceData
        let:preferences
        let:revlogRange>
        {#if controller}
            <svelte:component this={controller} {i18n} {search} {days} {loading} />
        {/if}

        {#if !pending}
            {#each graphs as graph}
                <svelte:component
                    this={graph}
                    {sourceData}
                    {preferences}
                    {revlogRange}
                    {i18n}
                    {nightMode}
                    on:search={(event) => browserSearch(search, event.detail.query)} />
            {/each}
        {/if}
    </WithGraphData>
</div>
