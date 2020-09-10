<script lang="typescript">
    import NoDataOverlay from "./NoDataOverlay.svelte";
    import AxisTicks from "./AxisTicks.svelte";
    import { defaultGraphBounds, RevlogRange } from "./graphs";
    import { GraphData, gatherData, renderCalendar } from "./calendar";
    import pb from "../backend/proto";
    import { I18n } from "../i18n";

    export let sourceData: pb.BackendProto.GraphsOut | null = null;
    export let revlogRange: RevlogRange;
    export let i18n: I18n;
    export let nightMode: boolean;

    let graphData: GraphData | null = null;

    let bounds = defaultGraphBounds();
    bounds.height = 120;
    bounds.marginLeft = 20;
    bounds.marginRight = 20;

    let svg = null as HTMLElement | SVGElement | null;
    let maxYear = new Date().getFullYear();
    let minYear = 0;
    let targetYear = maxYear;

    $: if (sourceData) {
        graphData = gatherData(sourceData);
    }

    $: {
        if (revlogRange < RevlogRange.Year) {
            minYear = maxYear;
        } else if ((revlogRange as RevlogRange) === RevlogRange.Year) {
            minYear = maxYear - 1;
        } else {
            minYear = 2000;
        }
        if (targetYear < minYear) {
            targetYear = minYear;
        }
    }

    $: if (graphData) {
        renderCalendar(
            svg as SVGElement,
            bounds,
            graphData,
            targetYear,
            i18n,
            nightMode,
            revlogRange,
        );
    }

    const title = i18n.tr(i18n.TR.STATISTICS_CALENDAR_TITLE);
</script>

<div class="graph" id="graph-calendar">
    <h1>{title}</h1>

    <div class="range-box-inner">
        <span>
            <button on:click={() => targetYear--} disabled={minYear >= targetYear}>
                ◄
            </button>
        </span>
        <span>{targetYear}</span>
        <span>
            <button on:click={() => targetYear++} disabled={targetYear >= maxYear}>
                ►
            </button>
        </span>
    </div>

    <svg bind:this={svg} viewBox={`0 0 ${bounds.width} ${bounds.height}`}>
        <g class="days" />
        <AxisTicks {bounds} />
        <NoDataOverlay {bounds} {i18n} />
    </svg>

</div>
