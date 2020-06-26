<script lang="typescript">
    import { gatherData, CardCounts } from "./card-counts";
    import pb from "../backend/proto";

    export let sourceData: pb.BackendProto.GraphsOut | null = null;

    let cardCounts: CardCounts | null = null;
    $: if (sourceData) {
        console.log("gathering data");
        cardCounts = gatherData(sourceData);
    }
</script>

<div class="graph">
    <h1>Card Counts</h1>
    {JSON.stringify(cardCounts)}
</div>
