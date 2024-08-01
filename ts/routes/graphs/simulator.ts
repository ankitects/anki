// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
import * as tr from "@generated/ftl";
import { localizedNumber } from "@tslib/i18n";
import { axisBottom, axisLeft, line, max, rollup, scaleLinear, select } from "d3";

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
    console.log(xMax);

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
    console.log(yMax);
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

    svg.selectAll("path").remove();

    const points = data.map((d) => [x(d.x), y(d.y), d.label]);
    const groups = rollup(points, v => Object.assign(v, { z: v[0][2] }), d => d[2]);

    svg.append("g")
        .attr("fill", "none")
        .attr("stroke", "green")
        .attr("stroke-width", 1.5)
        .attr("stroke-linejoin", "round")
        .attr("stroke-linecap", "round")
        .selectAll("path")
        .data(groups.values())
        .join("path")
        .style("mix-blend-mode", "multiply")
        .attr("d", line());

    setDataAvailable(svg, true);

    const tableData: TableDatum[] = [];

    return tableData;
}
