// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/* eslint
@typescript-eslint/no-non-null-assertion: "off",
@typescript-eslint/no-explicit-any: "off",
 */

import "d3-transition";
import { select, mouse } from "d3-selection";
import { cumsum, max, Bin } from "d3-array";
import { scaleLinear, ScaleLinear, ScaleSequential } from "d3-scale";
import { axisBottom, axisLeft } from "d3-axis";
import { area, curveBasis } from "d3-shape";
import { showTooltip, hideTooltip } from "./tooltip";
import { GraphBounds, setDataAvailable } from "./graphs";

export interface HistogramData {
    scale: ScaleLinear<number, number>;
    bins: Bin<number, number>[];
    total: number;
    hoverText: (
        data: HistogramData,
        binIdx: number,
        cumulative: number,
        percent: number
    ) => string;
    showArea: boolean;
    colourScale: ScaleSequential<string>;
    binValue?: (bin: Bin<any, any>) => number;
}

export function histogramGraph(
    svgElem: SVGElement,
    bounds: GraphBounds,
    data: HistogramData | null
): void {
    const svg = select(svgElem);
    const trans = svg.transition().duration(600) as any;

    if (!data) {
        setDataAvailable(svg, false);
        return;
    } else {
        setDataAvailable(svg, true);
    }

    const binValue = data.binValue ?? ((bin: any): number => bin.length as number);

    const x = data.scale.range([bounds.marginLeft, bounds.width - bounds.marginRight]);
    svg.select<SVGGElement>(".x-ticks")
        .transition(trans)
        .call(axisBottom(x).ticks(6).tickSizeOuter(0));

    // y scale

    const yMax = max(data.bins, (d) => binValue(d))!;
    const y = scaleLinear()
        .range([bounds.height - bounds.marginBottom, bounds.marginTop])
        .domain([0, yMax]);
    svg.select<SVGGElement>(".y-ticks")
        .transition(trans)
        .call(
            axisLeft(y)
                .ticks(bounds.height / 50)
                .tickSizeOuter(0)
        );

    // x bars

    function barWidth(d: any): number {
        const width = Math.max(0, x(d.x1) - x(d.x0) - 1);
        return width ? width : 0;
    }

    const updateBar = (sel: any): any => {
        return sel
            .attr("width", barWidth)
            .transition(trans)
            .attr("x", (d: any) => x(d.x0))
            .attr("y", (d: any) => y(binValue(d))!)
            .attr("height", (d: any) => y(0) - y(binValue(d)))
            .attr("fill", (d) => data.colourScale(d.x1));
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

    const areaCounts = data.bins.map((d) => binValue(d));
    areaCounts.unshift(0);
    const areaData = cumsum(areaCounts);
    const yAreaScale = y.copy().domain([0, data.total]);

    if (data.showArea && data.bins.length && areaData.slice(-1)[0]) {
        svg.select("path.area")
            .datum(areaData as any)
            .attr(
                "d",
                area()
                    .curve(curveBasis)
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
    }

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
            const pct = data.showArea ? (areaData[idx + 1] / data.total) * 100 : 0;
            showTooltip(data.hoverText(data, idx, areaData[idx + 1], pct), x, y);
        })
        .on("mouseout", hideTooltip);
}
