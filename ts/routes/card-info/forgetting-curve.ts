import {
    type CardStatsResponse_StatsRevlogEntry as RevlogEntry,
    RevlogEntry_ReviewKind,
} from "@generated/anki/stats_pb";
import { axisBottom, axisLeft, line, scaleLinear, select } from "d3";
import { type GraphBounds, setDataAvailable } from "../graphs/graph-helpers";

const FACTOR = 19 / 81;
const DECAY = -0.5;

function currentRetrievability(stability: number, daysElapsed: number): number {
    return Math.pow((daysElapsed / stability) * FACTOR + 1.0, DECAY);
}

interface DataPoint {
    daysElapsed: number;
    retrievability: number;
}

export enum TimeRange {
    Week,
    Month,
    Year,
    AllTime,
}

function filterDataByTimeRange(data: DataPoint[], range: TimeRange): DataPoint[] {
    const maxDays = {
        [TimeRange.Week]: 7,
        [TimeRange.Month]: 30,
        [TimeRange.Year]: 365,
        [TimeRange.AllTime]: Infinity,
    }[range];

    return data.filter((point) => point.daysElapsed <= maxDays);
}

export function filterRevlogByReviewKind(entry: RevlogEntry): boolean {
    return (
        entry.reviewKind !== RevlogEntry_ReviewKind.MANUAL
        && (entry.reviewKind !== RevlogEntry_ReviewKind.FILTERED || entry.ease !== 0)
    );
}

export function prepareData(revlog: RevlogEntry[], timeRange: TimeRange) {
    const data: DataPoint[] = [];
    let lastReviewTime = 0;
    let lastStability = 0;

    revlog
        .filter((entry) => filterRevlogByReviewKind(entry))
        .toReversed()
        .forEach((entry, index) => {
            const reviewTime = Number(entry.time);
            if (index === 0) {
                lastReviewTime = reviewTime;
                lastStability = entry.memoryState?.stability || 0;
                data.push({ daysElapsed: 0, retrievability: 100 });
                return;
            }

            const totalDaysElapsed = (reviewTime - lastReviewTime) / (24 * 60 * 60);

            const step = totalDaysElapsed / 20;
            for (let i = 0; i < 20; i++) {
                const day = (i + 1) * step;
                const retrievability = currentRetrievability(lastStability, day);
                data.push({
                    daysElapsed: data[data.length - 1].daysElapsed + step,
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

    if (data.length === 0) {
        return [];
    }

    const now = Date.now() / 1000;
    const daysSinceLastReview = Math.floor((now - lastReviewTime) / (24 * 60 * 60));

    const step = daysSinceLastReview / 20;
    for (let i = 0; i < 20; i++) {
        const day = (i + 1) * step;
        const retrievability = currentRetrievability(lastStability, day);
        data.push({
            daysElapsed: data[data.length - 1].daysElapsed + step,
            retrievability: retrievability * 100,
        });
    }
    const filteredData = filterDataByTimeRange(data, timeRange);
    return filteredData;
}

export function renderForgettingCurve(
    revlog: RevlogEntry[],
    timeRange: TimeRange,
    svgElem: SVGElement,
    bounds: GraphBounds,
) {
    const data = prepareData(revlog, timeRange);
    const svg = select(svgElem);
    const trans = svg.transition().duration(600) as any;

    if (data.length === 0) {
        setDataAvailable(svg, false);
        return;
    } else {
        setDataAvailable(svg, true);
    }

    svg.select(".forgetting-curve-line").remove();

    const xMax = Math.max(...data.map((d) => d.daysElapsed));
    const x = scaleLinear()
        .domain([0, xMax])
        .range([bounds.marginLeft, bounds.width - bounds.marginRight]);
    const yMin = Math.max(
        0,
        100 - 1.2 * (100 - Math.min(...data.map((d) => d.retrievability))),
    );
    const y = scaleLinear()
        .domain([yMin, 100])
        .range([bounds.height - bounds.marginBottom, bounds.marginTop]);

    svg.select<SVGGElement>(".x-ticks")
        .attr("transform", `translate(0,${bounds.height - bounds.marginBottom})`)
        .call((selection) =>
            selection
                .transition(trans)
                .call(axisBottom(x).ticks(5).tickSizeOuter(0))
        )
        .attr("direction", "ltr");

    svg.select<SVGGElement>(".y-ticks")
        .attr("transform", `translate(${bounds.marginLeft},0)`)
        .call((selection) => selection.transition(trans).call(axisLeft(y).tickSizeOuter(0)))
        .attr("direction", "ltr");

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
}
