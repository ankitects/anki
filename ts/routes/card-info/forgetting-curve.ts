// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import {
    type CardStatsResponse_StatsRevlogEntry as RevlogEntry,
    RevlogEntry_ReviewKind,
} from "@generated/anki/stats_pb";
import * as tr from "@generated/ftl";
import { timeSpan } from "@tslib/time";
import { axisBottom, axisLeft, line, pointer, scaleLinear, select } from "d3";
import { type GraphBounds, setDataAvailable } from "../graphs/graph-helpers";
import { hideTooltip, showTooltip } from "../graphs/tooltip-utils.svelte";

const FACTOR = 19 / 81;
const DECAY = -0.5;
const MIN_POINTS = 100;

function currentRetrievability(stability: number, daysElapsed: number): number {
    return Math.pow((daysElapsed / stability) * FACTOR + 1.0, DECAY);
}

interface DataPoint {
    daysSinceFirstLearn: number;
    elapsedDaysSinceLastReview: number;
    retrievability: number;
    stability: number;
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

    return data.filter((point) => point.daysSinceFirstLearn <= maxDays);
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
                data.push({
                    daysSinceFirstLearn: 0,
                    elapsedDaysSinceLastReview: 0,
                    retrievability: 100,
                    stability: lastStability,
                });
                return;
            }

            const totalDaysElapsed = (reviewTime - lastReviewTime) / (24 * 60 * 60);
            const step = Math.min(1, totalDaysElapsed / MIN_POINTS);
            for (let i = 0; i < Math.max(MIN_POINTS, totalDaysElapsed); i++) {
                const elapsedDays = (i + 1) * step;
                const retrievability = currentRetrievability(lastStability, elapsedDays);
                data.push({
                    daysSinceFirstLearn: data[data.length - 1].daysSinceFirstLearn + step,
                    elapsedDaysSinceLastReview: elapsedDays,
                    retrievability: retrievability * 100,
                    stability: lastStability,
                });
            }

            data.push({
                daysSinceFirstLearn: data[data.length - 1].daysSinceFirstLearn,
                retrievability: 100,
                elapsedDaysSinceLastReview: 0,
                stability: lastStability,
            });

            lastReviewTime = reviewTime;
            lastStability = entry.memoryState?.stability || 0;
        });

    if (data.length === 0) {
        return [];
    }

    const now = Date.now() / 1000;
    const totalDaysSinceLastReview = Math.floor((now - lastReviewTime) / (24 * 60 * 60));
    const step = Math.min(1, totalDaysSinceLastReview / MIN_POINTS);
    for (let i = 0; i < Math.max(MIN_POINTS, totalDaysSinceLastReview); i++) {
        const elapsedDays = (i + 1) * step;
        const retrievability = currentRetrievability(lastStability, elapsedDays);
        data.push({
            daysSinceFirstLearn: data[data.length - 1].daysSinceFirstLearn + step,
            elapsedDaysSinceLastReview: elapsedDays,
            retrievability: retrievability * 100,
            stability: lastStability,
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
    svg.select(".hover-columns").remove();

    const xMax = Math.max(...data.map((d) => d.daysSinceFirstLearn));
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
        .x((d) => x(d.daysSinceFirstLearn))
        .y((d) => y(d.retrievability));

    svg.append("path")
        .datum(data)
        .attr("class", "forgetting-curve-line")
        .attr("fill", "none")
        .attr("stroke", "steelblue")
        .attr("stroke-width", 1.5)
        .attr("d", lineGenerator);

    const focusLine = svg.append("line")
        .attr("class", "focus-line")
        .attr("y1", bounds.marginTop)
        .attr("y2", bounds.height - bounds.marginBottom)
        .attr("stroke", "black")
        .attr("stroke-width", 1)
        .style("opacity", 0);

    function tooltipText(d: DataPoint): string {
        return `${tr.cardStatsReviewLogElapsedTime()}: ${
            timeSpan(d.elapsedDaysSinceLastReview * 86400)
        }<br>${tr.cardStatsFsrsRetrievability()}: ${d.retrievability.toFixed(2)}%<br>${tr.cardStatsFsrsStability()}: ${
            timeSpan(d.stability * 86400)
        }`;
    }

    // hover/tooltip
    svg.append("g")
        .attr("class", "hover-columns")
        .selectAll("rect")
        .data(data)
        .join("rect")
        .attr("x", d => x(d.daysSinceFirstLearn) - 1)
        .attr("y", bounds.marginTop)
        .attr("width", 2)
        .attr("height", bounds.height - bounds.marginTop - bounds.marginBottom)
        .attr("fill", "transparent")
        .on("mousemove", (event: MouseEvent, d: DataPoint) => {
            const [x1, y1] = pointer(event, document.body);
            focusLine.attr("x1", x(d.daysSinceFirstLearn) - 1).attr("x2", x(d.daysSinceFirstLearn) + 1).style(
                "opacity",
                1,
            );
            showTooltip(tooltipText(d), x1, y1);
        })
        .on("mouseout", () => {
            focusLine.style("opacity", 0);
            hideTooltip();
        });
}
