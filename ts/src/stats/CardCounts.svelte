<script lang="typescript">
    import { gatherData, CardCounts } from "./card-counts";
    import pb from "../backend/proto";
    import { I18n } from "../i18n";

    export let sourceData: pb.BackendProto.GraphsOut | null = null;
    export let i18n: I18n;

    let cardCounts: CardCounts | null = null;
    $: if (sourceData) {
        cardCounts = gatherData(sourceData, i18n);
    }
</script>

<style>
    .counts-outer {
        display: flex;
        flex-wrap: wrap;
        justify-content: space-around;
    }
</style>

{#if cardCounts}
    <div class="graph">
        <h1>{cardCounts.title}</h1>
        <div class="counts-outer">
            {#each cardCounts.counts as count}
                <div>
                    <div>
                        <b>{count[0]}</b>
                    </div>
                    <div>{count[1]}</div>
                </div>
            {/each}
        </div>
    </div>
{/if}
