// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/* eslint
@typescript-eslint/no-explicit-any: "off",
 */

import type { GraphsResponse } from "@generated/anki/stats_pb";
import type { GraphsResponse_Hours_Hour } from "@generated/anki/stats_pb";
import * as tr from "@generated/ftl";
import { localizedNumber } from "@tslib/i18n";
import {
    area,
    axisBottom,
    axisLeft,
    axisRight,
    curveBasis,
    interpolateBlues,
    interpolatePurples,
    pointer,
    scaleBand,
    scaleLinear,
    scaleSequential,
    select,
} from "d3";

import type { GraphBounds } from "./graph-helpers";
import { GraphRange, setDataAvailable } from "./graph-helpers";
import { oddTickClass } from "./graph-styles";
import { hideTooltip, showTooltip } from "./tooltip-utils.svelte";

interface Hour {
    hour: number;
    totalCount: number;
    correctCount: number;
}

function gatherData(data: GraphsResponse, range: GraphRange): Hour[] {
    function convert(hours: GraphsResponse_Hours_Hour[]): Hour[] {
        return hours.map((e, idx) => {
            return { hour: idx, totalCount: e.total!, correctCount: e.correct! };
        });
    }
    switch (range) {
        case GraphRange.Month:
            return convert(data.hours!.oneMonth);
        case GraphRange.ThreeMonths:
            return convert(data.hours!.threeMonths);
        case GraphRange.Year:
            return convert(data.hours!.oneYear);
        case GraphRange.AllTime:
            return convert(data.hours!.allTime);
    }
}

export function renderHours(
    svgElem: SVGElement,
    bounds: GraphBounds,
    origData: GraphsResponse,
    range: GraphRange,
): void {
    const data = gatherData(origData, range);

    const yMax = Math.max(...data.map((d) => d.totalCount));

    const svg = select(svgElem);
    const trans = svg.transition().duration(600) as any;

    if (!yMax) {
        setDataAvailable(svg, false);
        return;
    } else {
        setDataAvailable(svg, true);
    }

    const x = scaleBand()
        .domain(data.map((d) => d.hour.toString()))
        .range([bounds.marginLeft, bounds.width - bounds.marginRight])
        .paddingInner(0.1);
    svg.select<SVGGElement>(".x-ticks")
        .call((selection) => selection.transition(trans).call(axisBottom(x).tickSizeOuter(0)))
        .selectAll(".tick")
        .selectAll("text")
        .classed(oddTickClass, (d: any): boolean => d % 2 != 0)
        .attr("direction", "ltr");

    let cappedRange = scaleLinear().range([0.1, 0.8]);
    let colour;
    const isColorBlindMode = (window as any).colorBlindMode;

    if(isColorBlindMode) { 
        colour = scaleSequential((n) => interpolatePurples(cappedRange(n)!)).domain([
            0,
            yMax,
        ]);
    } else {
        colour = scaleSequential((n) => interpolateBlues(cappedRange(n)!)).domain([
            0,
            yMax,
        ]);
    }

    // y scale
    const yTickFormat = (n: number): string => localizedNumber(n);

    const y = scaleLinear()
        .range([bounds.height - bounds.marginBottom, bounds.marginTop])
        .domain([0, yMax])
        .nice();
    svg.select<SVGGElement>(".y-ticks")
        .call((selection) =>
            selection.transition(trans).call(
                axisLeft(y)
                    .ticks(bounds.height / 50)
                    .tickSizeOuter(0)
                    .tickFormat(yTickFormat as any),
            )
        )
        .attr("direction", "ltr");

    const yArea = y.copy().domain([0, 1]);

    // x bars

    const updateBar = (sel: any): any => {
        return sel
            .attr("width", x.bandwidth())
            .transition(trans)
            .attr("x", (d: Hour) => x(d.hour.toString())!)
            .attr("y", (d: Hour) => y(d.totalCount)!)
            .attr("height", (d: Hour) => y(0)! - y(d.totalCount)!)
            .attr("fill", (d: Hour) => colour(d.totalCount!));
    };

    svg.select("g.bars")
        .selectAll("rect")
        .data(data)
        .join(
            (enter) =>
                enter
                    .append("rect")
                    .attr("rx", 1)
                    .attr("x", (d: Hour) => x(d.hour.toString())!)
                    .attr("y", y(0)!)
                    .attr("height", 0)
                    // .attr("opacity", 0.7)
                    .call(updateBar),
            (update) => update.call(updateBar),
            (remove) => remove.call((remove) => remove.transition(trans).attr("height", 0).attr("y", y(0)!)),
        );

    svg.select<SVGGElement>(".y2-ticks")
        .call((selection) =>
            selection.transition(trans).call(
                axisRight(yArea)
                    .ticks(bounds.height / 50)
                    .tickFormat((n: any) => `${Math.round(n * 100)}%`)
                    .tickSizeOuter(0),
            )
        )
        .attr("direction", "ltr");

    svg.select("path.cumulative-overlay")
        .datum(data)
        .attr(
            "d",
            area<Hour>()
                .curve(curveBasis)
                .defined((d) => d.totalCount > 0)
                .x((d: Hour) => {
                    return x(d.hour.toString())! + x.bandwidth() / 2;
                })
                .y0(bounds.height - bounds.marginBottom)
                .y1((d: Hour) => {
                    const correctRatio = d.correctCount! / d.totalCount!;
                    return yArea(isNaN(correctRatio) ? 0 : correctRatio)!;
                }),
        );

    function tooltipText(d: Hour): string {
        const hour = tr.statisticsHoursRange({
            hourStart: d.hour,
            hourEnd: d.hour + 1,
        });
        const reviews = tr.statisticsHoursReviews({ reviews: d.totalCount });
        const correct = tr.statisticsHoursCorrectReviews({
            percent: d.totalCount ? (d.correctCount / d.totalCount) * 100 : 0,
            reviews: d.correctCount,
        });
        return `${hour}<br>${reviews}<br>${correct}`;
    }

    // hover/tooltip
    svg.select("g.hover-columns")
        .selectAll("rect")
        .data(data)
        .join("rect")
        .attr("x", (d: Hour) => x(d.hour.toString())!)
        .attr("y", () => y(yMax)!)
        .attr("width", x.bandwidth())
        .attr("height", () => y(0)! - y(yMax!)!)
        .on("mousemove", (event: MouseEvent, d: Hour) => {
            const [x, y] = pointer(event, document.body);
            showTooltip(tooltipText(d), x, y);
        })
        .on("mouseout", hideTooltip);
}
