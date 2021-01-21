<script context="module">
</script>

<script lang="typescript">
    import type { SvelteComponent } from "svelte/internal";
    import type { I18n } from "anki/i18n";
    import type pb from "anki/backend_proto";
    import { getGraphData, getGraphPreferences, RevlogRange } from "./graph-helpers";

    export let i18n: I18n;
    export let nightMode: boolean;
    export let graphs: SvelteComponent[];

    export let search: string;
    export let days: number;
    export let controller: SvelteComponent | null;

    let active = false;
    let sourceData: pb.BackendProto.GraphsOut | null = null;
    let revlogRange: RevlogRange;

    const refreshWith = async (search: string, days: number) => {
        active = true;
        try {
            sourceData = await getGraphData(search, days);
            revlogRange = days > 365 || days === 0 ? RevlogRange.All : RevlogRange.Year;
        } catch (e) {
            sourceData = null;
            alert(i18n.tr(i18n.TR.STATISTICS_ERROR_FETCHING));
        }
        active = false;
    };

    const refresh = (event: CustomEvent) => {
        refreshWith(event.detail.search, event.detail.days);
    };

    refreshWith(search, days);
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
                {revlogRange}
                {i18n}
                {nightMode} />
        {/each}
    </div>
{/if}
