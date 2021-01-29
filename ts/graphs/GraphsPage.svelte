<script context="module">
</script>

<script lang="typescript">
    import type { SvelteComponent } from "svelte/internal";
    import type { I18n } from "anki/i18n";
    import type { PreferenceStore } from "./preferences";
    import type pb from "anki/backend_proto";
    import { getGraphData, RevlogRange, daysToRevlogRange } from "./graph-helpers";
    import { getPreferences } from "./preferences";
    import { bridgeCommand } from "anki/bridgecommand";

    export let i18n: I18n;
    export let nightMode: boolean;
    export let graphs: SvelteComponent[];

    export let search: string;
    export let days: number;
    export let controller: SvelteComponent | null;

    let active = false;
    let sourceData: pb.BackendProto.GraphsOut | null = null;
    let preferences: PreferenceStore | null = null;
    let revlogRange: RevlogRange;

    const preferencesPromise = getPreferences();

    const refreshWith = async (searchNew: string, days: number) => {
        search = searchNew;

        active = true;
        try {
            [sourceData, preferences] = await Promise.all([
                getGraphData(search, days),
                preferencesPromise,
            ]);
            revlogRange = daysToRevlogRange(days);
        } catch (e) {
            sourceData = null;
            alert(e);
        }
        active = false;
    };

    const refresh = (event: CustomEvent) => {
        refreshWith(event.detail.search, event.detail.days);
    };

    refreshWith(search, days);

    const browserSearch = (event: CustomEvent) => {
        const query = `${search} ${event.detail.query}`;
        bridgeCommand(`browserSearch:${query}`);
    };
</script>

{#if controller}
    <svelte:component
        this={controller}
        {i18n}
        {search}
        {days}
        {active}
        on:update={refresh} />
{/if}

{#if sourceData}
    <div tabindex="-1" class="no-focus-outline">
        {#each graphs as graph}
            <svelte:component
                this={graph}
                {sourceData}
                {preferences}
                {revlogRange}
                {i18n}
                {nightMode}
                on:search={browserSearch} />
        {/each}
    </div>
{/if}
