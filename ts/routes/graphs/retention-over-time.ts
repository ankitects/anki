// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/* eslint
@typescript-eslint/no-explicit-any: "off",
 */

import type { GraphsResponse, GraphsResponse_RetentionOverTime_Day } from "@generated/anki/stats_pb";
import * as tr from "@generated/ftl";
import { localizedNumber } from "@tslib/i18n";
import { axisBottom, axisLeft, line, pointer, scaleLinear, select } from "d3";

import type { GraphBounds } from "./graph-helpers";
import { GraphRange, setDataAvailable } from "./graph-helpers";
import { hideTooltip, showTooltip } from "./tooltip-utils.svelte";

/** Days in the rolling window, including the day itself. */
export const WINDOW_DAYS = 7;

export interface RetentionPoint {
    /** 0 is today, -1 yesterday. The window covers this day and the 6 before it. */
    day: number;
    reviews: number;
    /** Undefined when there were no reviews in the window. */
    retention?: number;
}

type DayTotals = Pick<GraphsResponse_RetentionOverTime_Day, "reviews" | "passed">;

function firstDay(days: Map<number, DayTotals>, range: GraphRange): number {
    switch (range) {
        case GraphRange.Month:
            return -30;
        case GraphRange.ThreeMonths:
            return -89;
        case GraphRange.Year:
            return -364;
        case GraphRange.AllTime:
            return Math.min(0, ...days.keys());
    }
}

/** One point per day: the totals of that day and the 6 days before it. */
export function rollingRetention(
    days: Map<number, DayTotals>,
    range: GraphRange,
): RetentionPoint[] {
    const start = firstDay(days, range);
    const points: RetentionPoint[] = [];
    let reviews = 0;
    let passed = 0;
    const add = (day: number, sign: number): void => {
        const totals = days.get(day);
        if (totals) {
            reviews += sign * totals.reviews;
            passed += sign * totals.passed;
        }
    };
    // prime the window with the days before the first point
    for (let day = start - WINDOW_DAYS + 1; day < start; day++) {
        add(day, 1);
    }
    for (let day = start; day <= 0; day++) {
        add(day, 1);
        points.push({
            day,
            reviews,
            retention: reviews ? passed / reviews : undefined,
        });
        add(day - WINDOW_DAYS + 1, -1);
    }
    return points;
}

function gatherDays(data: GraphsResponse): Map<number, DayTotals> {
    const days = new Map<number, DayTotals>();
    for (const [day, totals] of Object.entries(data.retentionOverTime?.days ?? {})) {
        days.set(Number(day), totals);
    }
    return days;
}

export interface TargetBand {
    min: number;
    max: number;
}

/** The time budget's minimum and maximum desired retention, if any. */
export function targetBand(data: GraphsResponse): TargetBand | undefined {
    const retention = data.retentionOverTime;
    if (retention?.targetMin === undefined || retention.targetMax === undefined) {
        return undefined;
    }
    return { min: retention.targetMin, max: retention.targetMax };
}

function percent(value: number): string {
    return localizedNumber(value * 100, 1);
}

export function renderRetentionOverTime(
    svgElem: SVGElement,
    bounds: GraphBounds,
    sourceData: GraphsResponse,
    range: GraphRange,
): void {
    const points = rollingRetention(gatherDays(sourceData), range);
    const svg = select(svgElem);
    const trans = svg.transition().duration(600) as any;

    const values = points.map((p) => p.retention).filter((v): v is number => v !== undefined);
    if (!values.length) {
        setDataAvailable(svg, false);
        return;
    }
    setDataAvailable(svg, true);

    const x = scaleLinear()
        .domain([points[0].day, 0])
        .range([bounds.marginLeft, bounds.width - bounds.marginRight]);
    svg.select<SVGGElement>(".x-ticks")
        .call((selection) =>
            selection.transition(trans).call(
                axisBottom(x)
                    .ticks(7)
                    .tickFormat((d: any) => localizedNumber(d))
                    .tickSizeOuter(0),
            )
        )
        .attr("direction", "ltr");

    // zoom in on the range in use (including the target band), in steps of 5%
    const band = targetBand(sourceData);
    const lowest = Math.min(...values, band?.min ?? 1);
    const yMin = Math.max(0, Math.floor((lowest - 0.02) * 20) / 20);
    const y = scaleLinear()
        .domain([yMin, 1])
        .range([bounds.height - bounds.marginBottom, bounds.marginTop]);
    svg.select<SVGGElement>(".y-ticks")
        .call((selection) =>
            selection.transition(trans).call(
                axisLeft(y)
                    .ticks(bounds.height / 50)
                    .tickFormat((n: any) => `${Math.round(n * 100)}%`)
                    .tickSizeOuter(0),
            )
        )
        .attr("direction", "ltr");

    // shade above the target (yellow), the target itself (green) and below
    // it (red)
    const shade = (klass: string, top: number, bottom: number): void => {
        svg.select(`rect.${klass}`)
            .attr("display", band ? null : "none")
            .attr("x", bounds.marginLeft)
            .attr("width", bounds.width - bounds.marginLeft - bounds.marginRight)
            .transition(trans)
            .attr("y", band ? y(top)! : 0)
            .attr("height", band ? Math.max(0, y(bottom)! - y(top)!) : 0);
    };
    shade("band-above", 1, band?.max ?? 1);
    shade("band-target", band?.max ?? 1, band?.min ?? 1);
    shade("band-below", band?.min ?? 1, yMin);

    const path = line<RetentionPoint>()
        .defined((p) => p.retention !== undefined)
        .x((p) => x(p.day)!)
        .y((p) => y(p.retention!)!)(points) ?? "";
    svg.select("path.retention-line").transition(trans).attr("d", path);

    function tooltipText(p: RetentionPoint): string {
        const lines = [
            tr.statisticsDaysAgoRange({ daysStart: -p.day, daysEnd: -p.day + WINDOW_DAYS - 1 }),
        ];
        if (p.retention !== undefined) {
            lines.push(
                tr.statisticsRetentionOverTimeValue({
                    percent: percent(p.retention),
                    reviews: p.reviews,
                }),
            );
        }
        return lines.join("<br>");
    }

    // one hover column per day
    const columnWidth = (bounds.width - bounds.marginLeft - bounds.marginRight) / points.length;
    svg.select("g.hover-columns")
        .selectAll("rect")
        .data(points)
        .join("rect")
        .attr("x", (p) => x(p.day)! - columnWidth / 2)
        .attr("y", bounds.marginTop)
        .attr("width", columnWidth)
        .attr("height", bounds.height - bounds.marginTop - bounds.marginBottom)
        .on("mousemove", (event: MouseEvent, p: RetentionPoint) => {
            const [px, py] = pointer(event, document.body);
            showTooltip(tooltipText(p), px, py);
        })
        .on("mouseout", hideTooltip);
}
