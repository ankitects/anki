<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import GraphsPage from "$lib/graphs/GraphsPage.svelte";

    import AddedGraph from "$lib/graphs/AddedGraph.svelte";
    import ButtonsGraph from "$lib/graphs/ButtonsGraph.svelte";
    import CalendarGraph from "$lib/graphs/CalendarGraph.svelte";
    import CardCounts from "$lib/graphs/CardCounts.svelte";
    import DifficultyGraph from "$lib/graphs/DifficultyGraph.svelte";
    import EaseGraph from "$lib/graphs/EaseGraph.svelte";
    import FutureDue from "$lib/graphs/FutureDue.svelte";
    import { RevlogRange } from "$lib/graphs/graph-helpers";
    import HourGraph from "$lib/graphs/HourGraph.svelte";
    import IntervalsGraph from "$lib/graphs/IntervalsGraph.svelte";
    import RangeBox from "$lib/graphs/RangeBox.svelte";
    import RetrievabilityGraph from "$lib/graphs/RetrievabilityGraph.svelte";
    import ReviewsGraph from "$lib/graphs/ReviewsGraph.svelte";
    import StabilityGraph from "$lib/graphs/StabilityGraph.svelte";
    import TodayStats from "$lib/graphs/TodayStats.svelte";

    const graphs = [
        TodayStats,
        FutureDue,
        CalendarGraph,
        ReviewsGraph,
        CardCounts,
        IntervalsGraph,
        StabilityGraph,
        EaseGraph,
        DifficultyGraph,
        RetrievabilityGraph,
        HourGraph,
        ButtonsGraph,
        AddedGraph,
    ];
</script>

<GraphsPage
    {graphs}
    initialSearch="deck:current"
    initialDays={365}
    controller={RangeBox}
/>
