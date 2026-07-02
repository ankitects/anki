// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/* eslint
@typescript-eslint/no-explicit-any: "off",
 */

import { localizedNumber } from "@tslib/i18n";
import type { Bin, ScaleLinear, ScaleSequential } from "d3";
import { area, axisBottom, axisLeft, axisRight, cumsum, curveBasis, max, pointer, scaleLinear, select } from "d3";

import type { GraphBounds } from "./graph-helpers";
import { setDataAvailable } from "./graph-helpers";
import { clickableClass } from "./graph-styles";
import { hideTooltip, showTooltip } from "./tooltip-utils.svelte";

export interface HistogramData {
    scale: ScaleLinear<number, number>;
    bins: Bin<number, number>[];
    total: number;
    hoverText: (
        bin: Bin<number, number>,
        cumulative: number,
        percent: number,
    ) => string;
    onClick: ((data: Bin<number, number>) => void) | null;
    showArea: boolean;
    colourScale: ScaleSequential<string>;
    binValue?: (bin: Bin<any, any>) => number;
    xTickFormat?: (d: any) => string;
}

export function histogramGraph(
    svgElem: SVGElement,
    bounds: GraphBounds,
    data: HistogramData | null,
): void {
    const svg = select(svgElem);
    const trans = svg.transition().duration(600) as any;
    const axisTickFormat = (n: number): string => localizedNumber(n);

    if (!data) {
        setDataAvailable(svg, false);
        return;
    } else {
        setDataAvailable(svg, true);
    }

    const binValue = data.binValue ?? ((bin: Bin<any, any>) => bin.length);

    const x = data.scale.range([bounds.marginLeft, bounds.width - bounds.marginRight]);
    svg.select<SVGGElement>(".x-ticks")
        .call((selection) =>
            selection.transition(trans).call(
                axisBottom(x)
                    .ticks(7)
                    .tickSizeOuter(0)
                    .tickFormat((data.xTickFormat ?? axisTickFormat) as any),
            )
        )
        .attr("direction", "ltr");

    // y scale

    const yMax = max(data.bins, (d) => binValue(d))!;
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
                    .tickFormat(axisTickFormat as any),
            )
        )
        .attr("direction", "ltr");

    // x bars

    function barWidth(d: Bin<number, number>): number {
        const width = Math.max(0, x(d.x1!) - x(d.x0!) - 1);
        return width ?? 0;
    }

    const updateBar = (sel: any): any => {
        return sel
            .attr("width", barWidth)
            .transition(trans)
            .attr("x", (d: any) => x(d.x0))
            .attr("y", (d: any) => y(binValue(d))!)
            .attr("height", (d: any) => y(0)! - y(binValue(d))!)
            .attr("fill", (d: any) => data.colourScale(d.x1));
    };

    svg.select("g.bars")
        .selectAll("rect")
        .data(data.bins)
        .join(
            (enter) =>
                enter
                    .append("rect")
                    .attr("rx", 1)
                    .attr("x", (d: any) => x(d.x0)!)
                    .attr("y", y(0)!)
                    .attr("height", 0)
                    .call(updateBar),
            (update) => update.call(updateBar),
            (remove) => remove.call((remove) => remove.transition(trans).attr("height", 0).attr("y", y(0)!)),
        );

    // cumulative area

    const areaCounts = data.bins.map((d) => binValue(d));
    areaCounts.unshift(0);
    const areaData = cumsum(areaCounts);
    const yAreaScale = y.copy().domain([0, data.total]).nice();

    if (data.showArea && data.bins.length && areaData.slice(-1)[0]) {
        svg.select<SVGGElement>(".y2-ticks")
            .call((selection) =>
                selection.transition(trans).call(
                    axisRight(yAreaScale)
                        .ticks(bounds.height / 50)
                        .tickSizeOuter(0)
                        .tickFormat(axisTickFormat as any),
                )
            )
            .attr("direction", "ltr");

        svg.select("path.cumulative-overlay")
            .datum(areaData as any)
            .attr(
                "d",
                area()
                    .curve(curveBasis)
                    .x((_d, idx) => {
                        if (idx === 0) {
                            return x(data.bins[0].x0!)!;
                        } else {
                            return x(data.bins[idx - 1].x1!)!;
                        }
                    })
                    .y0(bounds.height - bounds.marginBottom)
                    .y1((d: any) => yAreaScale(d)!) as any,
            );
    }

    const hoverData: [Bin<number, number>, number][] = data.bins.map(
        (bin: Bin<number, number>, index: number) => [bin, areaData[index + 1]],
    );

    // hover/tooltip
    const hoverzone = svg
        .select("g.hover-columns")
        .selectAll("rect")
        .data(hoverData)
        .join("rect")
        .attr("x", ([bin]) => x(bin.x0!))
        .attr("y", () => y(yMax))
        .attr("width", ([bin]) => barWidth(bin))
        .attr("height", () => y(0) - y(yMax))
        .on("mousemove", (event: MouseEvent, [bin, area]) => {
            const [x, y] = pointer(event, document.body);
            const pct = data.showArea ? (area / data.total) * 100 : 0;
            showTooltip(data.hoverText(bin, area, pct), x, y);
        })
        .on("mouseout", hideTooltip);

    if (data.onClick) {
        hoverzone
            .filter(([bin]) => bin.length > 0)
            .attr("class", clickableClass)
            .on("click", (_event, [bin]) => data.onClick!(bin));
    }
}
