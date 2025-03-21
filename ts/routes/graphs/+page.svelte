<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import AddedGraph from "./AddedGraph.svelte";
    import ButtonsGraph from "./ButtonsGraph.svelte";
    import CalendarGraph from "./CalendarGraph.svelte";
    import CardCounts from "./CardCounts.svelte";
    import DifficultyGraph from "./DifficultyGraph.svelte";
    import EaseGraph from "./EaseGraph.svelte";
    import FutureDue from "./FutureDue.svelte";
    import GraphsPage from "./GraphsPage.svelte";
    import HourGraph from "./HourGraph.svelte";
    import IntervalsGraph from "./IntervalsGraph.svelte";
    import RangeBox from "./RangeBox.svelte";
    import RetrievabilityGraph from "./RetrievabilityGraph.svelte";
    import ReviewsGraph from "./ReviewsGraph.svelte";
    import StabilityGraph from "./StabilityGraph.svelte";
    import TodayStats from "./TodayStats.svelte";
    import TrueRetention from "./TrueRetention.svelte";

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
        TrueRetention,
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
