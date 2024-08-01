// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
import * as tr from "@generated/ftl";
import { localizedNumber } from "@tslib/i18n";
import { axisBottom, axisLeft, line, max, rollup, scaleLinear, schemeCategory10, select } from "d3";

import type { GraphBounds, TableDatum } from "./graph-helpers";
import { setDataAvailable } from "./graph-helpers";
import { hideTooltip, showTooltip } from "./tooltip";

export interface Point {
    x: number;
    y: number;
    label: number;
}

export function renderSimulationChart(
    svgElem: SVGElement,
    bounds: GraphBounds,
    data: Point[],
): TableDatum[] {
    // Prepare data

    const svg = select(svgElem);
    const trans = svg.transition().duration(600) as any;

    const xMin = 0;
    const xMax = max(data, d => d.x);

    const x = scaleLinear().domain([xMin, xMax!]).range([bounds.marginLeft, bounds.width - bounds.marginRight]);
    svg.select<SVGGElement>(".x-ticks")
        .call((selection) => selection.transition(trans).call(axisBottom(x).ticks(7).tickSizeOuter(0)))
        .attr("direction", "ltr");

    // y scale

    const yTickFormat = (n: number): string => {
        if (Math.round(n) != n) {
            return "";
        } else {
            return localizedNumber(n);
        }
    };

    const yMax = max(data, d => d.y)!;
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

    // x lines
    const points = data.map((d) => [x(d.x), y(d.y), d.label]);
    const groups = rollup(points, v => Object.assign(v, { z: v[0][2] }), d => d[2]);

    const color = schemeCategory10;

    svg.selectAll("path").remove();

    svg.append("g")
        .attr("fill", "none")
        .attr("stroke-width", 1.5)
        .attr("stroke-linejoin", "round")
        .attr("stroke-linecap", "round")
        .selectAll("path")
        .data(Array.from(groups.entries()))
        .join("path")
        .style("mix-blend-mode", "multiply")
        .attr("stroke", (d, i) => color[i % color.length])
        .attr("d", d => line()(d[1].map(p => [p[0], p[1]])))
        .attr("data-group", d => d[0]);

    const legend = svg.append("g")
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
        .attr("x", bounds.width - bounds.marginRight + 10)
        .attr("width", 19)
        .attr("height", 19)
        .attr("fill", (d, i) => color[i % color.length]);

    legend.append("text")
        .attr("x", bounds.width - bounds.marginRight + 34)
        .attr("y", 9.5)
        .attr("dy", "0.32em")
        .text(d => `Group ${d}`);

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
