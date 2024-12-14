// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { localizedDate } from "@tslib/i18n";
import {
    axisBottom,
    axisLeft,
    bisector,
    line,
    max,
    pointer,
    rollup,
    scaleLinear,
    scaleTime,
    schemeCategory10,
    select,
} from "d3";

import * as tr from "@generated/ftl";
import { timeSpan } from "@tslib/time";
import type { GraphBounds, TableDatum } from "./graph-helpers";
import { setDataAvailable } from "./graph-helpers";
import { hideTooltip, showTooltip } from "./tooltip-utils.svelte";

export interface Point {
    x: number;
    timeCost: number;
    count: number;
    label: number;
}

export function renderSimulationChart(
    svgElem: SVGElement,
    bounds: GraphBounds,
    data: Point[],
    showTime: boolean,
): TableDatum[] {
    const svg = select(svgElem);
    svg.selectAll(".lines").remove();
    svg.selectAll(".hover-columns").remove();
    svg.selectAll(".focus-line").remove();
    svg.selectAll(".legend").remove();
    if (data.length == 0) {
        setDataAvailable(svg, false);
        return [];
    }
    const trans = svg.transition().duration(600) as any;

    // Prepare data
    const today = new Date();
    const convertedData = data.map(d => ({
        ...d,
        date: new Date(today.getTime() + d.x * 24 * 60 * 60 * 1000),
    }));
    const xMin = today;
    const xMax = max(convertedData, d => d.date);

    const x = scaleTime()
        .domain([xMin, xMax!])
        .range([bounds.marginLeft, bounds.width - bounds.marginRight]);

    svg.select<SVGGElement>(".x-ticks")
        .call((selection) => selection.transition(trans).call(axisBottom(x).ticks(7).tickSizeOuter(0)))
        .attr("direction", "ltr");
    // y scale

    const yTickFormat = (n: number): string => {
        return showTime ? timeSpan(n, true) : n.toString();
    };

    const yMax = showTime ? max(convertedData, d => d.timeCost)! : max(convertedData, d => d.count)!;
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

    svg.select(".y-ticks .y-axis-title").remove();
    svg.select(".y-ticks")
        .append("text")
        .attr("class", "y-axis-title")
        .attr("transform", "rotate(-90)")
        .attr("y", 0 - bounds.marginLeft)
        .attr("x", 0 - (bounds.height / 2))
        .attr("font-size", "1rem")
        .attr("dy", "1.1em")
        .attr("fill", "currentColor")
        .style("text-anchor", "middle")
        .text(`${
            showTime
                ? tr.deckConfigFsrsSimulatorYAxisTitleTime()
                : tr.deckConfigFsrsSimulatorYAxisTitleCount()
        }`);

    // x lines
    const points = convertedData.map((d) => [x(d.date), y(showTime ? d.timeCost : d.count), d.label]);
    const groups = rollup(points, v => Object.assign(v, { z: v[0][2] }), d => d[2]);

    const color = schemeCategory10;

    svg.append("g")
        .attr("class", "lines")
        .attr("fill", "none")
        .attr("stroke-width", 1.5)
        .attr("stroke-linejoin", "round")
        .attr("stroke-linecap", "round")
        .selectAll("path")
        .data(Array.from(groups.entries()))
        .join("path")
        .attr("stroke", (d, i) => color[i % color.length])
        .attr("d", d => line()(d[1].map(p => [p[0], p[1]])))
        .attr("data-group", d => d[0]);

    const focusLine = svg.append("line")
        .attr("class", "focus-line")
        .attr("y1", bounds.marginTop)
        .attr("y2", bounds.height - bounds.marginBottom)
        .attr("stroke", "black")
        .attr("stroke-width", 1)
        .style("opacity", 0);

    const LongestGroupData = Array.from(groups.values()).reduce((a, b) => a.length > b.length ? a : b);
    const barWidth = bounds.width / LongestGroupData.length;

    // hover/tooltip
    svg.append("g")
        .attr("class", "hover-columns")
        .selectAll("rect")
        .data(LongestGroupData)
        .join("rect")
        .attr("x", d => d[0] - barWidth / 2)
        .attr("y", bounds.marginTop)
        .attr("width", barWidth)
        .attr("height", bounds.height - bounds.marginTop - bounds.marginBottom)
        .attr("fill", "transparent")
        .on("mousemove", mousemove)
        .on("mouseout", () => {
            focusLine.style("opacity", 0);
            hideTooltip();
        });

    function mousemove(event: MouseEvent, d: any): void {
        pointer(event, document.body);
        const date = x.invert(d[0]);

        const groupData: { [key: string]: number } = {};

        groups.forEach((groupPoints, key) => {
            const bisect = bisector((d: number[]) => x.invert(d[0])).left;
            const index = bisect(groupPoints, date);
            const dataPoint = groupPoints[index - 1] || groupPoints[index];

            if (dataPoint) {
                groupData[key] = y.invert(dataPoint[1]);
            }
        });

        focusLine.attr("x1", d[0]).attr("x2", d[0]).style("opacity", 1);

        const days = +((date.getTime() - Date.now()) / (60 * 60 * 24 * 1000)).toFixed();
        let tooltipContent = `Date: ${localizedDate(date)}<br>In ${days} Days<br>`;
        for (const [key, value] of Object.entries(groupData)) {
            tooltipContent += `#${key}: ${
                showTime ? timeSpan(value) : tr.statisticsReviews({ reviews: Math.round(value) })
            }<br>`;
        }

        showTooltip(tooltipContent, event.pageX, event.pageY);
    }

    const legend = svg.append("g")
        .attr("class", "legend")
        .attr("font-family", "sans-serif")
        .attr("font-size", 10)
        .attr("text-anchor", "start")
        .selectAll("g")
        .data(Array.from(groups.keys()))
        .join("g")
        .attr("transform", (d, i) => `translate(0,${i * 20})`)
        .attr("cursor", "pointer")
        .on("click", (event, d) => toggleGroup(event, d));

    legend.append("rect")
        .attr("x", bounds.width - bounds.marginRight + 36)
        .attr("width", 12)
        .attr("height", 12)
        .attr("fill", (d, i) => color[i % color.length]);

    legend.append("text")
        .attr("x", bounds.width - bounds.marginRight + 52)
        .attr("y", 7)
        .attr("dy", "0.3em")
        .attr("fill", "currentColor")
        .text(d => `#${d}`);

    const toggleGroup = (event: MouseEvent, d: number) => {
        const group = d;
        const path = svg.select(`path[data-group="${group}"]`);
        const hidden = path.classed("hidden");
        const target = event.currentTarget as HTMLElement;

        path.classed("hidden", !hidden);
        path.style("display", () => hidden ? null : "none");

        select(target).select("rect")
            .style("opacity", hidden ? 1 : 0.5);
    };

    setDataAvailable(svg, true);

    const tableData: TableDatum[] = [];

    return tableData;
}
