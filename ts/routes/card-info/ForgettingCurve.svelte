<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import type { CardStatsResponse_StatsRevlogEntry as RevlogEntry } from "@generated/anki/stats_pb";
    import { axisBottom, axisLeft, line, max, min, scaleLinear, select } from "d3";
    import { onMount } from "svelte";
    import { defaultGraphBounds, setDataAvailable } from "../graphs/graph-helpers";
    import Graph from "../graphs/Graph.svelte";
    import NoDataOverlay from "../graphs/NoDataOverlay.svelte";
    import AxisTicks from "../graphs/AxisTicks.svelte";

    export let revlog: RevlogEntry[];
    let svg = null as HTMLElement | SVGElement | null;
    const bounds = defaultGraphBounds();

    const FACTOR = 19 / 81;
    const DECAY = -0.5;

    function currentRetrievability(stability: number, daysElapsed: number): number {
        return Math.pow((daysElapsed / stability) * FACTOR + 1.0, DECAY);
    }

    interface DataPoint {
        daysElapsed: number;
        retrievability: number;
    }

    function prepareData() {
        let data: DataPoint[] = [];
        let lastReviewTime = 0;
        let lastStability = 0;

        revlog.reverse().forEach((entry, index) => {
            const reviewTime = Number(entry.time);
            if (index === 0) {
                lastReviewTime = reviewTime;
                lastStability = entry.memoryState?.stability || 0;
                data.push({ daysElapsed: 0, retrievability: 100 });
                return;
            }

            const totalDaysElapsed = (reviewTime - lastReviewTime) / (24 * 60 * 60);

            for (let day = 1; day <= Math.ceil(totalDaysElapsed); day++) {
                const retrievability = currentRetrievability(lastStability, day);
                data.push({
                    daysElapsed: data[data.length - 1].daysElapsed + 1,
                    retrievability: retrievability * 100,
                });
            }

            data.push({
                daysElapsed: data[data.length - 1].daysElapsed,
                retrievability: 100,
            });

            lastReviewTime = reviewTime;
            lastStability = entry.memoryState?.stability || 0;
        });

        const now = Date.now() / 1000;
        const daysSinceLastReview = Math.floor((now - lastReviewTime) / (24 * 60 * 60));

        for (let day = 1; day <= daysSinceLastReview; day++) {
            const retrievability = currentRetrievability(lastStability, day);
            data.push({
                daysElapsed: data[data.length - 1].daysElapsed + 1,
                retrievability: retrievability * 100,
            });
        }

        return data;
    }
    function renderForgettingCurve(svgElem: SVGElement) {
        const data = prepareData();
        const svg = select(svgElem);

        const trans = svg.transition().duration(600) as any;

        const x = scaleLinear()
            .domain([0, max(data, (d) => d.daysElapsed) || 0])
            .range([bounds.marginLeft, bounds.width - bounds.marginRight]);

        const y = scaleLinear()
            .domain([
                Math.max(
                    0,
                    100 - 1.2 * (100 - (min(data, (d) => d.retrievability) ?? 0)),
                ),
                100,
            ])
            .range([bounds.height - bounds.marginBottom, bounds.marginTop]);

        svg.select<SVGGElement>(".x-ticks")
            .attr("transform", `translate(0,${bounds.height - bounds.marginBottom})`)
            .call((selection) =>
                selection
                    .transition(trans)
                    .call(axisBottom(x).ticks(5).tickSizeOuter(0)),
            )
            .attr("direction", "ltr");

        svg.select(".x-ticks")
            .append("text")
            .attr("class", "x-axis-title")
            .attr("text-anchor", "middle")
            .attr("x", bounds.width / 2)
            .attr("y", bounds.marginBottom)
            .attr("fill", "currentColor")
            .text("Days since first review");

        svg.select<SVGGElement>(".y-ticks")
            .attr("transform", `translate(${bounds.marginLeft},0)`)
            .call((selection) =>
                selection.transition(trans).call(axisLeft(y).tickSizeOuter(0)),
            )
            .attr("direction", "ltr");

        svg.select(".y-ticks")
            .append("text")
            .attr("class", "y-axis-title")
            .attr("transform", "rotate(-90)")
            .attr("y", 0 - bounds.marginLeft)
            .attr("x", 0 - bounds.height / 2)
            .attr("dy", "1em")
            .attr("fill", "currentColor")
            .style("text-anchor", "middle")
            .text("Retrievability (%)");

        const lineGenerator = line<DataPoint>()
            .x((d) => x(d.daysElapsed))
            .y((d) => y(d.retrievability));

        svg.append("path")
            .datum(data)
            .attr("class", "forgetting-curve-line")
            .attr("fill", "none")
            .attr("stroke", "steelblue")
            .attr("stroke-width", 1.5)
            .attr("d", lineGenerator);

        setDataAvailable(svg, data.length > 0);
    }

    onMount(() => {
        renderForgettingCurve(svg as SVGElement);
    });

    const title = "Forgetting Curve";
</script>

<div class="forgetting-curve">
    <Graph {title}>
        <svg bind:this={svg} viewBox={`0 0 ${bounds.width} ${bounds.height}`}>
            <AxisTicks {bounds} />
            <NoDataOverlay {bounds} />
        </svg>
    </Graph>
</div>

<style>
    .forgetting-curve {
        width: 100%;
        max-width: 50em;
    }
</style>
