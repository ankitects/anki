// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import {
    type CardStatsResponse_StatsRevlogEntry as RevlogEntry,
    RevlogEntry_ReviewKind,
} from "@generated/anki/stats_pb";
import * as tr from "@generated/ftl";
import { timeSpan } from "@tslib/time";
import { axisBottom, axisLeft, line, max, min, pointer, scaleLinear, scaleTime, select } from "d3";
import { type GraphBounds, setDataAvailable } from "../graphs/graph-helpers";
import { hideTooltip, showTooltip } from "../graphs/tooltip-utils.svelte";

const MIN_POINTS = 1000;

function forgettingCurve(stability: number, daysElapsed: number, decay: number): number {
    const factor = Math.pow(0.9, 1 / -decay) - 1;
    return Math.pow((daysElapsed / stability) * factor + 1.0, -decay);
}

interface DataPoint {
    date: Date;
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

const MAX_DAYS = {
    [TimeRange.Week]: 7,
    [TimeRange.Month]: 30,
    [TimeRange.Year]: 365,
    [TimeRange.AllTime]: Infinity,
};

function filterDataByTimeRange(data: DataPoint[], maxDays: number): DataPoint[] {
    return data.filter((point) => point.daysSinceFirstLearn <= maxDays);
}

export function filterRevlogEntryByReviewKind(entry: RevlogEntry): boolean {
    return (
        entry.reviewKind !== RevlogEntry_ReviewKind.MANUAL
        && entry.reviewKind !== RevlogEntry_ReviewKind.RESCHEDULED
        && (entry.reviewKind !== RevlogEntry_ReviewKind.FILTERED || entry.ease !== 0)
    );
}

export function filterRevlog(revlog: RevlogEntry[]): RevlogEntry[] {
    const result: RevlogEntry[] = [];
    for (const entry of revlog) {
        if (
            (entry.reviewKind === RevlogEntry_ReviewKind.MANUAL && entry.ease === 0)
            || entry.memoryState === undefined
        ) {
            break;
        }
        result.push(entry);
    }

    return result.filter((entry) => filterRevlogEntryByReviewKind(entry));
}

export function prepareData(revlog: RevlogEntry[], maxDays: number, decay: number) {
    const data: DataPoint[] = [];
    let lastReviewTime = 0;
    let lastStability = 0;
    const step = Math.min(maxDays / MIN_POINTS, 1);
    let daysSinceFirstLearn = 0;

    revlog
        .slice()
        .reverse()
        .forEach((entry, index) => {
            const reviewTime = Number(entry.time);
            if (index === 0) {
                lastReviewTime = reviewTime;
                lastStability = entry.memoryState?.stability || 0;
                data.push({
                    date: new Date(reviewTime * 1000),
                    daysSinceFirstLearn: 0,
                    elapsedDaysSinceLastReview: 0,
                    retrievability: 100,
                    stability: lastStability,
                });
                return;
            }

            const totalDaysElapsed = (reviewTime - lastReviewTime) / 86400;
            let elapsedDays = 0;
            while (elapsedDays < totalDaysElapsed - step) {
                elapsedDays += step;
                const retrievability = forgettingCurve(lastStability, elapsedDays, decay);
                data.push({
                    date: new Date((lastReviewTime + elapsedDays * 86400) * 1000),
                    daysSinceFirstLearn: data[data.length - 1].daysSinceFirstLearn + step,
                    elapsedDaysSinceLastReview: elapsedDays,
                    retrievability: retrievability * 100,
                    stability: lastStability,
                });
            }
            daysSinceFirstLearn += totalDaysElapsed;
            data.push({
                date: new Date((lastReviewTime + totalDaysElapsed * 86400) * 1000),
                daysSinceFirstLearn: daysSinceFirstLearn,
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
    const totalDaysSinceLastReview = (now - lastReviewTime) / 86400;
    let elapsedDays = 0;
    while (elapsedDays < totalDaysSinceLastReview - step) {
        elapsedDays += step;
        const retrievability = forgettingCurve(lastStability, elapsedDays, decay);
        data.push({
            date: new Date((lastReviewTime + elapsedDays * 86400) * 1000),
            daysSinceFirstLearn: data[data.length - 1].daysSinceFirstLearn + step,
            elapsedDaysSinceLastReview: elapsedDays,
            retrievability: retrievability * 100,
            stability: lastStability,
        });
    }
    daysSinceFirstLearn += totalDaysSinceLastReview;
    const retrievability = forgettingCurve(lastStability, totalDaysSinceLastReview, decay);
    data.push({
        date: new Date(now * 1000),
        daysSinceFirstLearn: daysSinceFirstLearn,
        elapsedDaysSinceLastReview: totalDaysSinceLastReview,
        retrievability: retrievability * 100,
        stability: lastStability,
    });

    const previewDays = maxDays - totalDaysSinceLastReview;
    let previewDaysElapsed = 0;
    while (previewDaysElapsed < previewDays) {
        previewDaysElapsed += step;
        const retrievability = forgettingCurve(lastStability, elapsedDays + previewDaysElapsed, decay);
        data.push({
            date: new Date((now + previewDaysElapsed * 86400) * 1000),
            daysSinceFirstLearn: data[data.length - 1].daysSinceFirstLearn + step,
            elapsedDaysSinceLastReview: totalDaysSinceLastReview + previewDaysElapsed,
            retrievability: retrievability * 100,
            stability: lastStability,
        });
    }

    const filteredData = filterDataByTimeRange(data, maxDays);
    return filteredData;
}

export function calculateMaxDays(filteredRevlog: RevlogEntry[], timeRange: TimeRange): number {
    if (filteredRevlog.length === 0) {
        return 0;
    }
    const today = new Date();
    const daysSinceFirstLearn = (today.getTime() / 1000 - Number(filteredRevlog[filteredRevlog.length - 1].time))
        / 86400;
    const totalDaysSinceLastReview = (today.getTime() / 1000 - Number(filteredRevlog[0].time))
        / 86400;
    const lastScheduledDays = filteredRevlog[0].interval / 86400;
    const previewDays = Math.max(lastScheduledDays * 1.5 - totalDaysSinceLastReview, lastScheduledDays * 0.5);
    return Math.min(daysSinceFirstLearn + previewDays, MAX_DAYS[timeRange]);
}

export function renderForgettingCurve(
    filteredRevlog: RevlogEntry[],
    timeRange: TimeRange,
    svgElem: SVGElement,
    bounds: GraphBounds,
    desiredRetention: number,
    decay: number,
) {
    const svg = select(svgElem);
    const trans = svg.transition().duration(600) as any;
    if (filteredRevlog.length === 0) {
        setDataAvailable(svg, false);
        return;
    }
    const maxDays = calculateMaxDays(filteredRevlog, timeRange);

    const data = prepareData(filteredRevlog, maxDays, decay);

    if (data.length === 0) {
        setDataAvailable(svg, false);
        return;
    } else {
        setDataAvailable(svg, true);
    }

    svg.selectAll(".forgetting-curve-line").remove();
    svg.select(".hover-columns").remove();

    const xMin = min(data, d => d.date);
    const xMax = max(data, d => d.date);
    const x = scaleTime()
        .domain([xMin!, xMax!])
        .range([bounds.marginLeft, bounds.width - bounds.marginRight]);
    const yMin = Math.max(
        0,
        100 - 1.2 * (100 - Math.min(...data.map((d) => d.retrievability))),
    );
    const y = scaleLinear()
        .domain([yMin, 100])
        .range([bounds.height - bounds.marginBottom, bounds.marginTop]);

    svg.select<SVGGElement>(".x-ticks")
        .call((selection) => selection.transition(trans).call(axisBottom(x).ticks(5).tickSizeOuter(0)))
        .attr("direction", "ltr");

    svg.select<SVGGElement>(".y-ticks")
        .attr("transform", `translate(${bounds.marginLeft},0)`)
        .call((selection) => selection.transition(trans).call(axisLeft(y).tickSizeOuter(0)))
        .attr("direction", "ltr");

    svg.select(".y-ticks .y-axis-title").remove();
    svg.select(".y-ticks")
        .append("text")
        .attr("class", "y-axis-title")
        .attr("transform", "rotate(-90)")
        .attr("y", 0 - bounds.marginLeft)
        .attr("x", 0 - (bounds.height / 2))
        .attr("font-size", "1rem")
        .attr("dy", "1.1em")
        .attr("fill", "currentColor");

    const lineGenerator = line<DataPoint>()
        .x((d) => x(d.date))
        .y((d) => y(d.retrievability));

    // gradient color
    const desiredRetentionY = desiredRetention * 100;
    svg.append("linearGradient")
        .attr("id", "line-gradient")
        .attr("gradientUnits", "userSpaceOnUse")
        .attr("x1", 0)
        .attr("y1", y(0))
        .attr("x2", 0)
        .attr("y2", y(100))
        .selectAll("stop")
        .data([
            { offset: "0%", color: "tomato" },
            { offset: `${desiredRetentionY}%`, color: "steelblue" },
            { offset: "100%", color: "green" },
        ])
        .enter().append("stop")
        .attr("offset", d => d.offset)
        .attr("stop-color", d => d.color);

    // Split data into past and future
    const today = new Date();
    const pastData = data.filter(d => d.date <= today);
    const futureData = data.filter(d => d.date >= today);

    // Draw solid line for past data
    svg.append("path")
        .datum(pastData)
        .attr("class", "forgetting-curve-line")
        .attr("fill", "none")
        .attr("stroke", "url(#line-gradient)")
        .attr("stroke-width", 1.5)
        .attr("d", lineGenerator);

    // Draw dashed line for future data
    svg.append("path")
        .datum(futureData)
        .attr("class", "forgetting-curve-line")
        .attr("fill", "none")
        .attr("stroke", "url(#line-gradient)")
        .attr("stroke-width", 1.5)
        .attr("stroke-dasharray", "4 4")
        .attr("d", lineGenerator);

    svg.select(".desired-retention-line").remove();
    if (desiredRetentionY > yMin) {
        svg.append("line")
            .attr("class", "desired-retention-line")
            .attr("x1", bounds.marginLeft)
            .attr("x2", bounds.width - bounds.marginRight)
            .attr("y1", y(desiredRetentionY))
            .attr("y2", y(desiredRetentionY))
            .attr("stroke", "steelblue")
            .attr("stroke-dasharray", "4 4")
            .attr("stroke-width", 1.2);
    }

    const focusLine = svg.append("line")
        .attr("class", "focus-line")
        .attr("y1", bounds.marginTop)
        .attr("y2", bounds.height - bounds.marginBottom)
        .attr("stroke", "black")
        .attr("stroke-width", 1)
        .style("opacity", 0);

    function tooltipText(d: DataPoint): string {
        return `${maxDays >= 365 ? "Date" : "Date Time"}: ${
            maxDays >= 365 ? d.date.toLocaleDateString() : d.date.toLocaleString()
        }<br>
        ${tr.cardStatsReviewLogElapsedTime()}: ${
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
        .attr("x", d => x(d.date) - 1)
        .attr("y", bounds.marginTop)
        .attr("width", 2)
        .attr("height", bounds.height - bounds.marginTop - bounds.marginBottom)
        .attr("fill", "transparent")
        .on("mousemove", (event: MouseEvent, d: DataPoint) => {
            const [x1, y1] = pointer(event, document.body);
            const [_, y2] = pointer(event, svg.node());

            const lineY = y(desiredRetentionY);
            focusLine.attr("x1", x(d.date) - 1).attr("x2", x(d.date) + 1).style(
                "opacity",
                1,
            );
            let text = tooltipText(d);
            if (y2 >= lineY - 10 && y2 <= lineY + 10) {
                text += `<br>${tr.cardStatsFsrsForgettingCurveDesiredRetention()}: ${desiredRetention.toFixed(2) * 100}%`;
            }
            showTooltip(text, x1, y1);
        })
        .on("mouseout", () => {
            focusLine.style("opacity", 0);
            hideTooltip();
        });
}
