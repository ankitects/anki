// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/* eslint
@typescript-eslint/no-non-null-assertion: "off",
@typescript-eslint/no-explicit-any: "off",
@typescript-eslint/ban-ts-ignore: "off",
@typescript-eslint/explicit-function-return-type: "off" */

import { select, mouse, Selection } from "d3-selection";
import { cumsum, extent, max, histogram, quantile } from "d3-array";
import { interpolateBlues } from "d3-scale-chromatic";
import { scaleLinear, scaleSequential } from "d3-scale";
import { axisBottom, axisLeft } from "d3-axis";
import { area } from "d3-shape";
import "d3-transition";
import { CardQueue } from "../cards";
import { showTooltip, hideTooltip } from "./tooltip";
import pb from "../backend/proto";
import { assertUnreachable } from "../typing";

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

export function gatherIntervalData(cards: pb.BackendProto.Card[]): IntervalGraphData {
    const intervals = cards
        .filter((c) => c.queue == CardQueue.Review)
        .map((c) => c.ivl);
    return { intervals };
}

export type IntervalUpdateFn = (
    arg0: IntervalGraphData,
    maxDays: IntervalRange
) => void;

/// Creates an interval graph, returning a function used to update it.
export function intervalGraph(svgElem: SVGElement): IntervalUpdateFn {
    const margin = { top: 20, right: 20, bottom: 40, left: 100 };
    const height = 250;
    const width = 600;
    const xTicks = 6;

    // svg elements
    const svg = select(svgElem).attr("viewBox", [0, 0, width, height].join(" "));
    const barGroup = svg.append("g");
    const hoverGroup = svg.append("g");
    const areaPath = svg.select("path.area");
    const xAxisGroup = svg.append("g").classed("no-domain-line", true);
    const yAxisGroup = svg.append("g").classed("no-domain-line", true);

    // x axis
    const xScale = scaleLinear()
        .range([margin.left, width - margin.right])
        .domain([0, 0]);
    svg.append("text")
        .attr("transform", `translate(${width / 2}, ${height - 5})`)
        .style("text-anchor", "middle")
        .style("font-size", 10)
        .text("Interval (days)");

    // y axis
    const yScale = scaleLinear()
        .domain([0, 0])
        .range([height - margin.bottom, margin.top]);
    svg.append("text")
        .attr(
            "transform",
            `translate(${margin.left / 3}, ${
                (height - margin.bottom) / 2 + margin.top
            }) rotate(-180)`
        )
        .style("text-anchor", "middle")
        .style("writing-mode", "vertical-rl")
        .style("rotate", "180")
        .style("font-size", 10)
        .text("Number of cards");

    function update(graphData: IntervalGraphData, maxDays: IntervalRange) {
        const allIntervals = graphData.intervals;

        const [xMin, origXMax] = extent(allIntervals);

        let desiredBars = 70;
        let xMax = origXMax;
        switch (maxDays) {
            case IntervalRange.Month:
                xMax = Math.min(xMax!, 31);
                desiredBars = 31;
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
            default:
                assertUnreachable(maxDays);
        }

        const x = xScale.copy().domain([xMin!, xMax!]);
        // .nice();

        const data = histogram()
            .domain(x.domain() as any)
            .thresholds(x.ticks(desiredBars))(allIntervals);

        const yMax = max(data, (d) => d.length);

        const colourScale = scaleSequential(interpolateBlues).domain([
            -20,
            data.length,
        ]);

        const y = yScale.copy().domain([0, yMax!]);

        const t = svg.transition().duration(600);

        const updateXAxis = (
            g: Selection<SVGGElement, unknown, null, undefined>,
            scale: any
        ) =>
            g
                .attr("transform", `translate(0,${height - margin.bottom})`)
                .call(axisBottom(scale).ticks(xTicks).tickSizeOuter(0));

        xAxisGroup.transition(t as any).call(updateXAxis as any, x);

        const updateYAxis = (
            g: Selection<SVGGElement, unknown, null, undefined>,
            scale: any
        ) =>
            g.attr("transform", `translate(${margin.left}, 0)`).call(
                axisLeft(scale)
                    .ticks(height / 80)
                    .tickSizeOuter(0)
            );

        yAxisGroup.transition(t as any).call(updateYAxis as any, y);

        const updateBar = (sel: any) => {
            return sel.call((sel) =>
                sel
                    .transition(t as any)
                    .attr("width", (d: any) => Math.max(0, x(d.x1) - x(d.x0) - 1))
                    .attr("x", (d: any) => x(d.x0))
                    .attr("y", (d: any) => y(d.length)!)
                    .attr("height", (d: any) => y(0) - y(d.length))
                    .attr("fill", (d, idx) => colourScale(idx))
            );
        };

        barGroup
            .selectAll("rect")
            .data(data)
            .join(
                (enter) =>
                    updateBar(
                        enter
                            .append("rect")
                            .attr("rx", 1)
                            .attr("x", (d: any) => x(d.x0))
                            .attr("y", y(0))
                            .attr("height", 0)
                    ),
                (update) => updateBar(update),
                (remove) =>
                    remove.call((remove) =>
                        remove
                            .transition(t as any)
                            .attr("height", 0)
                            .attr("y", y(0))
                    )
            );

        const areaData = cumsum(data.map((d) => d.length));
        const xAreaScale = x.copy().domain([0, areaData.length]);
        const yAreaScale = y.copy().domain([0, allIntervals.length]);

        areaPath
            .datum(areaData as any)
            .attr("fill", "grey")
            .attr(
                "d",
                area()
                    .x((d: any, idx) => {
                        return xAreaScale(idx);
                    })
                    .y0(height - margin.bottom)
                    .y1((d: any) => yAreaScale(d)) as any
            );

        hoverGroup
            .selectAll("rect")
            .data(data)
            .join("rect")
            .attr("x", (d: any) => x(d.x0))
            .attr("y", () => y(yMax!))
            .attr("width", (d: any) => Math.max(0, x(d.x1) - x(d.x0) - 1))
            .attr("height", () => y(0) - y(yMax!))
            .attr("fill", "none")
            .attr("pointer-events", "all")
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

    return update;
}
