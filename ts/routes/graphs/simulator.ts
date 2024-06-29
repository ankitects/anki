// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
import type { SimulateFsrsReviewResponse } from "@generated/anki/scheduler_pb";
import * as tr from "@generated/ftl";
import { localizedNumber } from "@tslib/i18n";
import { axisBottom, axisLeft, line, max, scaleLinear, select } from "d3";

import type { GraphBounds, TableDatum } from "./graph-helpers";
import { setDataAvailable } from "./graph-helpers";
import { hideTooltip, showTooltip } from "./tooltip";

export function renderSimulationChart(
    svgElem: SVGElement,
    bounds: GraphBounds,
    data: SimulateFsrsReviewResponse,
): TableDatum[] {
    // Prepare data

    const svg = select(svgElem);
    const trans = svg.transition().duration(600) as any;

    const xMax = data.dailyReviewCount.length - 1;
    const xMin = 0;

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

    const yMax = max(data.dailyReviewCount)!;
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

    svg.select("g.lines0")
        .append("path")
        .datum(data.dailyReviewCount)
        .attr("fill", "none")
        .attr("stroke", "steelblue")
        .attr("stroke-width", 1.5)
        .attr(
            "d",
            line<number>()
                .x((d, i) => x(i))
                .y(d => y(d)),
        );

    setDataAvailable(svg, true);

    const tableData: TableDatum[] = [];

    return tableData;
}
