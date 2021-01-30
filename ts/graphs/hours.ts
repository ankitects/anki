// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/* eslint
@typescript-eslint/no-non-null-assertion: "off",
@typescript-eslint/no-explicit-any: "off",
 */

import pb from "anki/backend_proto";
import {
    interpolateBlues,
    select,
    pointer,
    scaleLinear,
    scaleBand,
    scaleSequential,
    axisBottom,
    axisLeft,
    axisRight,
    area,
    curveBasis,
} from "d3";
import { showTooltip, hideTooltip } from "./tooltip";
import {
    GraphBounds,
    setDataAvailable,
    GraphRange,
    millisecondCutoffForRange,
} from "./graph-helpers";
import type { I18n } from "anki/i18n";

type ButtonCounts = [number, number, number, number];

interface Hour {
    hour: number;
    totalCount: number;
    correctCount: number;
}

const ReviewKind = pb.BackendProto.RevlogEntry.ReviewKind;

function gatherData(data: pb.BackendProto.GraphsOut, range: GraphRange): Hour[] {
    const hours = [...Array(24)].map((_n, idx: number) => {
        return { hour: idx, totalCount: 0, correctCount: 0 } as Hour;
    });
    const cutoff = millisecondCutoffForRange(range, data.nextDayAtSecs);

    for (const review of data.revlog as pb.BackendProto.RevlogEntry[]) {
        if (review.reviewKind == ReviewKind.EARLY_REVIEW) {
            continue;
        }
        if (cutoff && (review.id as number) < cutoff) {
            continue;
        }

        const hour = Math.floor(
            (((review.id as number) / 1000 + data.localOffsetSecs) / 3600) % 24
        );
        hours[hour].totalCount += 1;
        if (review.buttonChosen != 1) {
            hours[hour].correctCount += 1;
        }
    }

    return hours;
}

export function renderHours(
    svgElem: SVGElement,
    bounds: GraphBounds,
    origData: pb.BackendProto.GraphsOut,
    i18n: I18n,
    range: GraphRange
): void {
    const data = gatherData(origData, range);

    const yMax = Math.max(...data.map((d) => d.totalCount));

    const svg = select(svgElem);
    const trans = svg.transition().duration(600) as any;

    if (!yMax) {
        setDataAvailable(svg, false);
        return;
    } else {
        setDataAvailable(svg, true);
    }

    const x = scaleBand()
        .domain(data.map((d) => d.hour.toString()))
        .range([bounds.marginLeft, bounds.width - bounds.marginRight])
        .paddingInner(0.1);
    svg.select<SVGGElement>(".x-ticks")
        .transition(trans)
        .call(axisBottom(x).tickSizeOuter(0))
        .selectAll("text")
        .attr("class", (n: any) => {
            if (n % 2 != 0) {
                return "tick-odd";
            } else {
                return "";
            }
        });

    const cappedRange = scaleLinear().range([0.1, 0.8]);
    const colour = scaleSequential((n) => interpolateBlues(cappedRange(n)!)).domain([
        0,
        yMax,
    ]);

    // y scale

    const y = scaleLinear()
        .range([bounds.height - bounds.marginBottom, bounds.marginTop])
        .domain([0, yMax])
        .nice();
    svg.select<SVGGElement>(".y-ticks")
        .transition(trans)
        .call(
            axisLeft(y)
                .ticks(bounds.height / 50)
                .tickSizeOuter(0)
        );

    const yArea = y.copy().domain([0, 1]);

    // x bars

    const updateBar = (sel: any): any => {
        return sel
            .attr("width", x.bandwidth())
            .transition(trans)
            .attr("x", (d: Hour) => x(d.hour.toString())!)
            .attr("y", (d: Hour) => y(d.totalCount)!)
            .attr("height", (d: Hour) => y(0)! - y(d.totalCount)!)
            .attr("fill", (d: Hour) => colour(d.totalCount!));
    };

    svg.select("g.bars")
        .selectAll("rect")
        .data(data)
        .join(
            (enter) =>
                enter
                    .append("rect")
                    .attr("rx", 1)
                    .attr("x", (d: Hour) => x(d.hour.toString())!)
                    .attr("y", y(0)!)
                    .attr("height", 0)
                    // .attr("opacity", 0.7)
                    .call(updateBar),
            (update) => update.call(updateBar),
            (remove) =>
                remove.call((remove) =>
                    remove.transition(trans).attr("height", 0).attr("y", y(0)!)
                )
        );

    svg.select<SVGGElement>(".y2-ticks")
        .transition(trans)
        .call(
            axisRight(yArea)
                .ticks(bounds.height / 50)
                .tickFormat((n: any) => `${Math.round(n * 100)}%`)
                .tickSizeOuter(0)
        );

    svg.select("path.area")
        .datum(data)
        .attr(
            "d",
            area<Hour>()
                .curve(curveBasis)
                .defined((d) => d.totalCount > 0)
                .x((d: Hour) => {
                    return x(d.hour.toString())! + x.bandwidth() / 2;
                })
                .y0(bounds.height - bounds.marginBottom)
                .y1((d: Hour) => {
                    const correctRatio = d.correctCount! / d.totalCount!;
                    return yArea(isNaN(correctRatio) ? 0 : correctRatio)!;
                })
        );

    function tooltipText(d: Hour): string {
        const hour = i18n.tr(i18n.TR.STATISTICS_HOURS_RANGE, {
            hourStart: d.hour,
            hourEnd: d.hour + 1,
        });
        const correct = i18n.tr(i18n.TR.STATISTICS_HOURS_CORRECT, {
            correct: d.correctCount,
            total: d.totalCount,
            percent: d.totalCount ? (d.correctCount / d.totalCount) * 100 : 0,
        });
        return `${hour}<br>${correct}`;
    }

    // hover/tooltip
    svg.select("g.hoverzone")
        .selectAll("rect")
        .data(data)
        .join("rect")
        .attr("x", (d: Hour) => x(d.hour.toString())!)
        .attr("y", () => y(yMax)!)
        .attr("width", x.bandwidth())
        .attr("height", () => y(0)! - y(yMax!)!)
        .on("mousemove", (event: MouseEvent, d: Hour) => {
            const [x, y] = pointer(event, document.body);
            showTooltip(tooltipText(d), x, y);
        })
        .on("mouseout", hideTooltip);
}
