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
import { sumBy } from "lodash-es";
import type { GraphBounds, TableDatum } from "./graph-helpers";
import { setDataAvailable } from "./graph-helpers";
import { hideTooltip, showTooltip } from "./tooltip-utils.svelte";

export interface Point {
    x: number;
    timeCost: number;
    count: number;
    memorized: number;
    label: number;
}

export enum SimulateSubgraph {
    time,
    count,
    memorized,
}

export enum SimulateWorkloadSubgraph {
    ratio,
    time,
    memorized,
}

export function renderWorkloadChart(
    svgElem: SVGElement,
    bounds: GraphBounds,
    data: Point[],
    subgraph: SimulateWorkloadSubgraph,
) {
    const xMin = 70;
    const xMax = 99;

    const x = scaleLinear()
        .domain([xMin, xMax])
        .range([bounds.marginLeft, bounds.width - bounds.marginRight]);

    const subgraph_data = ({
        [SimulateWorkloadSubgraph.ratio]: data.map(d => ({ ...d, y: d.timeCost / d.memorized })),
        [SimulateWorkloadSubgraph.time]: data.map(d => ({ ...d, y: d.timeCost })),
        [SimulateWorkloadSubgraph.memorized]: data.map(d => ({ ...d, y: d.memorized })),
    })[subgraph];

    const yTickFormat = (n: number): string => {
        return subgraph == SimulateWorkloadSubgraph.time ? timeSpan(n, true) : n.toString();
    };

    const formatY: (value: number) => string = ({
        [SimulateWorkloadSubgraph.ratio]: (value: number) => `${timeSpan(value)} time per 1 card memorized`,
        [SimulateWorkloadSubgraph.time]: timeSpan,
        [SimulateWorkloadSubgraph.memorized]: (value: number) =>
            tr.statisticsMemorized({ memorized: Math.round(value).toFixed(0) }),
    })[subgraph];

    function formatX(dr: number) {
        return `Desired Retention: ${dr}%<br>`;
    }

    return _renderSimulationChart(
        svgElem,
        bounds,
        subgraph_data,
        x,
        yTickFormat,
        formatY,
        formatX,
        (_e: MouseEvent, _d: number) => undefined,
    );
}

export function renderSimulationChart(
    svgElem: SVGElement,
    bounds: GraphBounds,
    data: Point[],
    subgraph: SimulateSubgraph,
): TableDatum[] {
    const today = new Date();
    const convertedData = data.map(d => ({
        ...d,
        x: new Date(today.getTime() + d.x * 24 * 60 * 60 * 1000),
    }));

    const subgraph_data = ({
        [SimulateSubgraph.count]: convertedData.map(d => ({ ...d, y: d.count })),
        [SimulateSubgraph.time]: convertedData.map(d => ({ ...d, y: d.timeCost })),
        [SimulateSubgraph.memorized]: convertedData.map(d => ({ ...d, y: d.memorized })),
    })[subgraph];

    const xMin = today;
    const xMax = max(subgraph_data, d => d.x);

    const x = scaleTime()
        .domain([xMin, xMax!])
        .range([bounds.marginLeft, bounds.width - bounds.marginRight]);

    const yTickFormat = (n: number): string => {
        return subgraph == SimulateSubgraph.time ? timeSpan(n, true) : n.toString();
    };

    const formatY: (value: number) => string = ({
        [SimulateSubgraph.time]: timeSpan,
        [SimulateSubgraph.count]: (value: number) => tr.statisticsReviews({ reviews: Math.round(value) }),
        [SimulateSubgraph.memorized]: (value: number) =>
            tr.statisticsMemorized({ memorized: Math.round(value).toFixed(0) }),
    })[subgraph];

    const perDay = ({
        [SimulateSubgraph.count]: tr.statisticsReviewsPerDay,
        [SimulateSubgraph.time]: ({ count }: { count: number }) => timeSpan(count),
        [SimulateSubgraph.memorized]: tr.statisticsCardsPerDay,
    })[subgraph];

    function legendMouseMove(e: MouseEvent, d: number) {
        const data = subgraph_data.filter(datum => datum.label == d);

        const total = subgraph == SimulateSubgraph.memorized
            ? data[data.length - 1].memorized - data[0].memorized
            : sumBy(data, d => d.y);
        const average = total / (data?.length || 1);

        showTooltip(
            `#${d}:<br/>
                ${tr.statisticsAverage()}: ${perDay({ count: average })}<br/>
                ${tr.statisticsTotal()}: ${formatY(total)}`,
            e.pageX,
            e.pageY,
        );
    }

    function formatX(date: Date) {
        const days = +((date.getTime() - Date.now()) / (60 * 60 * 24 * 1000)).toFixed();
        return `Date: ${localizedDate(date)}<br>In ${days} Days<br>`;
    }

    return _renderSimulationChart(
        svgElem,
        bounds,
        subgraph_data,
        x,
        yTickFormat,
        formatY,
        formatX,
        legendMouseMove,
    );
}

function _renderSimulationChart<T extends { x: any; y: any; label: number }>(
    svgElem: SVGElement,
    bounds: GraphBounds,
    subgraph_data: T[],
    x: any,
    yTickFormat: (n: number) => string,
    formatY: (n: T["y"]) => string,
    formatX: (n: T["x"]) => string,
    legendMouseMove: (e: MouseEvent, d: number) => void,
): TableDatum[] {
    const svg = select(svgElem);
    svg.selectAll(".lines").remove();
    svg.selectAll(".hover-columns").remove();
    svg.selectAll(".focus-line").remove();
    svg.selectAll(".legend").remove();
    if (subgraph_data.length == 0) {
        setDataAvailable(svg, false);
        return [];
    }
    const trans = svg.transition().duration(600) as any;

    svg.select<SVGGElement>(".x-ticks")
        .call((selection) => selection.transition(trans).call(axisBottom(x).ticks(7).tickSizeOuter(0)))
        .attr("direction", "ltr");
    // y scale

    const yMax = max(subgraph_data, d => d.y)!;
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
        .attr("fill", "currentColor");

    // x lines
    const points = subgraph_data.map((d) => [x(d.x), y(d.y), d.label]);
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
        .attr("vector-effect", "non-scaling-stroke")
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
            const dataPoint = groupPoints[index];

            if (dataPoint) {
                groupData[key] = y.invert(dataPoint[1]);
            }
        });

        focusLine.attr("x1", d[0]).attr("x2", d[0]).style("opacity", 1);

        let tooltipContent = formatX(date);
        for (const [key, value] of Object.entries(groupData)) {
            const path = svg.select(`path[data-group="${key}"]`);
            const hidden = path.classed("hidden");

            if (!hidden) {
                tooltipContent += `<span style="color:${color[(parseInt(key) - 1) % color.length]}">■</span> #${key}: ${
                    formatY(value)
                }<br>`;
            }
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
        .on("click", (event, d) => toggleGroup(event, d))
        .on("mousemove", legendMouseMove)
        .on("mouseout", hideTooltip);

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
