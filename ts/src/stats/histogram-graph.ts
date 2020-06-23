// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/* eslint
@typescript-eslint/no-non-null-assertion: "off",
@typescript-eslint/no-explicit-any: "off",
 */

import "d3-transition";
import { select, mouse } from "d3-selection";
import { cumsum, max, Bin } from "d3-array";
import { interpolateBlues } from "d3-scale-chromatic";
import { scaleLinear, scaleSequential, ScaleLinear } from "d3-scale";
import { axisBottom, axisLeft } from "d3-axis";
import { area } from "d3-shape";
import { showTooltip, hideTooltip } from "./tooltip";
import { GraphBounds } from "./graphs";

export interface HistogramData {
    scale: ScaleLinear<number, number>;
    bins: Bin<number, number>[];
    total: number;
}

export function histogramGraph(
    svgElem: SVGElement,
    bounds: GraphBounds,
    data: HistogramData
): void {
    const svg = select(svgElem);
    const trans = svg.transition().duration(600) as any;

    const x = data.scale.range([bounds.marginLeft, bounds.width - bounds.marginRight]);
    svg.select<SVGGElement>(".x-ticks")
        .transition(trans)
        .call(axisBottom(x).ticks(6).tickSizeOuter(0));

    // y scale

    const yMax = max(data.bins, (d) => d.length)!;
    const y = scaleLinear()
        .range([bounds.height - bounds.marginBottom, bounds.marginTop])
        .domain([0, yMax]);
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

    const colour = scaleSequential(interpolateBlues).domain([-5, data.bins.length]);

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
        .data(data.bins)
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

    const areaCounts = data.bins.map((d) => d.length);
    areaCounts.unshift(0);
    const areaData = cumsum(areaCounts);
    const yAreaScale = y.copy().domain([0, data.total]);

    svg.select("path.area")
        .datum(areaData as any)
        .attr(
            "d",
            area()
                .x((d, idx) => {
                    if (idx === 0) {
                        return x(data.bins[0].x0!);
                    } else {
                        return x(data.bins[idx - 1].x1!);
                    }
                })
                .y0(bounds.height - bounds.marginBottom)
                .y1((d: any) => yAreaScale(d)) as any
        );

    // hover/tooltip

    svg.select("g.hoverzone")
        .selectAll("rect")
        .data(data.bins)
        .join("rect")
        .attr("x", (d: any) => x(d.x0))
        .attr("y", () => y(yMax!))
        .attr("width", barWidth)
        .attr("height", () => y(0) - y(yMax!))
        .on("mousemove", function (this: any, d: any, idx) {
            const [x, y] = mouse(document.body);
            const pct = ((areaData[idx] / data.total) * 100).toFixed(2);
            showTooltip(
                `${d.length} cards with interval ${d.x0}~${d.x1} days. ` +
                    `<br>${pct}% cards below this point.`,
                x,
                y
            );
        })
        .on("mouseout", hideTooltip);
}
