// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/* eslint
@typescript-eslint/no-non-null-assertion: "off",
@typescript-eslint/no-explicit-any: "off",
 */

import "d3-transition";
import pb from "../backend/proto";
import { select, mouse } from "d3-selection";
import { cumsum, extent, max, histogram, quantile } from "d3-array";
import { interpolateBlues } from "d3-scale-chromatic";
import { scaleLinear, scaleSequential } from "d3-scale";
import { axisBottom, axisLeft } from "d3-axis";
import { area } from "d3-shape";
import { CardQueue } from "../cards";
import { showTooltip, hideTooltip } from "./tooltip";
import { GraphBounds } from "./graphs";

export interface IntervalGraphData {
    intervals: number[];
}

export enum IntervalRange {
    Month = 0,
    Percentile50 = 1,
    Percentile95 = 2,
    Percentile999 = 3,
    All = 4,
}

export function gatherIntervalData(data: pb.BackendProto.GraphsOut): IntervalGraphData {
    const intervals = (data.cards2 as pb.BackendProto.Card[])
        .filter((c) => c.queue == CardQueue.Review)
        .map((c) => c.ivl);
    return { intervals };
}

export function intervalGraph(
    svgElem: SVGElement,
    bounds: GraphBounds,
    graphData: IntervalGraphData,
    maxDays: IntervalRange
): void {
    const svg = select(svgElem);
    const trans = svg.transition().duration(600) as any;

    const allIntervals = graphData.intervals;

    const [xMin, origXMax] = extent(allIntervals);
    let xMax = origXMax;
    switch (maxDays) {
        case IntervalRange.Month:
            xMax = Math.min(xMax!, 31);
            break;
        case IntervalRange.Percentile50:
            xMax = quantile(allIntervals, 0.5);
            break;
        case IntervalRange.Percentile95:
            xMax = quantile(allIntervals, 0.95);
            break;
        case IntervalRange.Percentile999:
            xMax = quantile(allIntervals, 0.999);
            break;
        case IntervalRange.All:
            break;
    }
    const desiredBars = Math.min(70, xMax! - xMin!);

    // x scale & bins

    const x = scaleLinear()
        .range([bounds.marginLeft, bounds.width - bounds.marginRight])
        .domain([xMin!, xMax!]);
    const data = histogram()
        .domain(x.domain() as any)
        .thresholds(x.ticks(desiredBars))(allIntervals);
    svg.select<SVGGElement>(".x-ticks")
        .transition(trans)
        .call(axisBottom(x).ticks(6).tickSizeOuter(0));

    // y scale

    const yMax = max(data, (d) => d.length);
    const y = scaleLinear()
        .range([bounds.height - bounds.marginBottom, bounds.marginTop])
        .domain([0, yMax!]);
    svg.select<SVGGElement>(".y-ticks")
        .transition(trans)
        .call(
            axisLeft(y)
                .ticks(bounds.height / 80)
                .tickSizeOuter(0)
        );

    // x bars

    function barWidth(d: any): number {
        const width = Math.max(0, x(d.x1) - x(d.x0) - 1);
        return width ? width : 0;
    }

    const colour = scaleSequential(interpolateBlues).domain([-5, data.length]);

    const updateBar = (sel: any): any => {
        return sel
            .transition(trans)
            .attr("width", barWidth)
            .attr("x", (d: any) => x(d.x0))
            .attr("y", (d: any) => y(d.length)!)
            .attr("height", (d: any) => y(0) - y(d.length))
            .attr("fill", (d, idx) => colour(idx));
    };

    svg.select("g.bars")
        .selectAll("rect")
        .data(data)
        .join(
            (enter) =>
                enter
                    .append("rect")
                    .attr("rx", 1)
                    .attr("x", (d: any) => x(d.x0))
                    .attr("y", y(0))
                    .attr("height", 0)
                    .call(updateBar),
            (update) => update.call(updateBar),
            (remove) =>
                remove.call((remove) =>
                    remove.transition(trans).attr("height", 0).attr("y", y(0))
                )
        );

    // cumulative area

    const areaData = cumsum(data.map((d) => d.length));
    const xAreaScale = x.copy().domain([0, areaData.length]);
    const yAreaScale = y.copy().domain([0, allIntervals.length]);

    svg.select("path.area")
        .datum(areaData as any)
        .attr(
            "d",
            area()
                .x((d, idx) => {
                    return xAreaScale(idx);
                })
                .y0(bounds.height - bounds.marginBottom)
                .y1((d: any) => yAreaScale(d)) as any
        );

    // hover/tooltip

    svg.select("g.hoverzone")
        .selectAll("rect")
        .data(data)
        .join("rect")
        .attr("x", (d: any) => x(d.x0))
        .attr("y", () => y(yMax!))
        .attr("width", barWidth)
        .attr("height", () => y(0) - y(yMax!))
        .on("mousemove", function (this: any, d: any, idx) {
            const [x, y] = mouse(document.body);
            const pct = ((areaData[idx] / allIntervals.length) * 100).toFixed(2);
            showTooltip(
                `${d.length} cards with interval ${d.x0}~${d.x1} days. ` +
                    `<br>${pct}% cards below this point.`,
                x,
                y
            );
        })
        .on("mouseout", hideTooltip);
}
