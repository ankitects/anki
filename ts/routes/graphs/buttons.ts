// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/* eslint
@typescript-eslint/no-explicit-any: "off",
 */

import type { GraphsResponse } from "@generated/anki/stats_pb";
import * as tr from "@generated/ftl";
import { localizedNumber } from "@tslib/i18n";
import {
    axisBottom,
    axisLeft,
    interpolateRdYlGn,
    pointer,
    scaleBand,
    scaleLinear,
    scaleSequential,
    select,
    sum,
} from "d3";

import type { GraphBounds } from "./graph-helpers";
import { GraphRange } from "./graph-helpers";
import { setDataAvailable } from "./graph-helpers";
import { hideTooltip, showTooltip } from "./tooltip-utils.svelte";

/** 4 element array */
type ButtonCounts = number[];

export interface GraphData {
    learning: ButtonCounts;
    young: ButtonCounts;
    mature: ButtonCounts;
}

export function gatherData(data: GraphsResponse, range: GraphRange): GraphData {
    const buttons = data.buttons!;
    switch (range) {
        case GraphRange.Month:
            return buttons.oneMonth!;
        case GraphRange.ThreeMonths:
            return buttons.threeMonths!;
        case GraphRange.Year:
            return buttons.oneYear!;
        case GraphRange.AllTime:
            return buttons.allTime!;
    }
}

type GroupKind = "learning" | "young" | "mature";

interface Datum {
    buttonNum: number;
    group: GroupKind;
    count: number;
}

interface TotalCorrect {
    total: number;
    correct: number;
    percent: string;
}

export function renderButtons(
    svgElem: SVGElement,
    bounds: GraphBounds,
    origData: GraphsResponse,
    range: GraphRange,
): void {
    const sourceData = gatherData(origData, range);
    const data = [
        ...sourceData.learning.map((count: number, idx: number) => {
            return {
                buttonNum: idx + 1,
                group: "learning",
                count,
            } satisfies Datum;
        }),
        ...sourceData.young.map((count: number, idx: number) => {
            return {
                buttonNum: idx + 1,
                group: "young",
                count,
            } satisfies Datum;
        }),
        ...sourceData.mature.map((count: number, idx: number) => {
            return {
                buttonNum: idx + 1,
                group: "mature",
                count,
            } satisfies Datum;
        }),
    ];

    const totalCorrect = (kind: GroupKind): TotalCorrect => {
        const groupData = data.filter((d) => d.group == kind);
        const total = sum(groupData, (d) => d.count);
        const correct = sum(
            groupData.filter((d) => d.buttonNum > 1),
            (d) => d.count,
        );
        const percent = total ? localizedNumber((correct / total) * 100) : "0";
        return { total, correct, percent };
    };

    const totalPressedStr = (data: Datum): string => {
        const groupTotal = totalCorrect(data.group).total;
        const buttonTotal = data.count;
        const percent = groupTotal
            ? localizedNumber((buttonTotal / groupTotal) * 100)
            : "0";

        return `${localizedNumber(buttonTotal)} (${percent}%)`;
    };

    const yMax = Math.max(...data.map((d) => d.count));

    const svg = select(svgElem);
    const trans = svg.transition().duration(600) as any;

    if (!yMax) {
        setDataAvailable(svg, false);
        return;
    } else {
        setDataAvailable(svg, true);
    }

    const xGroup = scaleBand()
        .domain(["learning", "young", "mature"])
        .range([bounds.marginLeft, bounds.width - bounds.marginRight]);
    svg.select<SVGGElement>(".x-ticks")
        .call((selection) =>
            selection.transition(trans).call(
                axisBottom(xGroup)
                    .tickFormat(
                        ((d: GroupKind) => {
                            let kind: string;
                            switch (d) {
                                case "learning":
                                    kind = tr.statisticsCountsLearningCards();
                                    break;
                                case "young":
                                    kind = tr.statisticsCountsYoungCards();
                                    break;
                                case "mature":
                                default:
                                    kind = tr.statisticsCountsMatureCards();
                                    break;
                            }
                            return `${kind}`;
                        }) as any,
                    )
                    .tickSizeOuter(0),
            )
        )
        .attr("direction", "ltr");

    svg.select<SVGGElement>(".x-ticks").selectAll<SVGTextElement, GroupKind>("text")
        .each(function(this: SVGTextElement, d: GroupKind) {
            if (!(this instanceof SVGElement)) {
                return;
            }
            const current_text_element_content = this.textContent;

            this.textContent = "";

            // Create a tspan for the text content (the "kind" part)
            const tspan_kind = document.createElementNS("http://www.w3.org/2000/svg", "tspan");
            tspan_kind.textContent = current_text_element_content;
            tspan_kind.setAttribute("dy", "0.5em");

            // Create a tspan for the percentage
            const tspan_percentage = document.createElementNS("http://www.w3.org/2000/svg", "tspan");
            tspan_percentage.textContent = `\u200e(${totalCorrect(d).percent}%)`;
            tspan_percentage.setAttribute("dy", "1em");
            tspan_percentage.setAttribute("x", "0");

            this.appendChild(tspan_kind);
            this.appendChild(tspan_percentage);
        });

    const xButton = scaleBand()
        .domain(["1", "2", "3", "4"])
        .range([0, xGroup.bandwidth()])
        .paddingOuter(1)
        .paddingInner(0.1);

    const colour = scaleSequential(interpolateRdYlGn).domain([1, 4]);

    // y scale
    const yTickFormat = (n: number): string => localizedNumber(n);

    const y = scaleLinear()
        .range([bounds.height - bounds.marginBottom, bounds.marginTop])
        .domain([0, yMax]);
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

    // x bars

    const updateBar = (sel: any): any => {
        return sel
            .attr("width", xButton.bandwidth())
            .attr("opacity", 0.8)
            .transition(trans)
            .attr(
                "x",
                (d: Datum) => xGroup(d.group)! + xButton(d.buttonNum.toString())!,
            )
            .attr("y", (d: Datum) => y(d.count)!)
            .attr("height", (d: Datum) => y(0)! - y(d.count)!)
            .attr("fill", (d: Datum) => colour(d.buttonNum));
    };

    svg.select("g.bars")
        .selectAll("rect")
        .data(data)
        .join(
            (enter) =>
                enter
                    .append("rect")
                    .attr("rx", 1)
                    .attr(
                        "x",
                        (d: Datum) => xGroup(d.group)! + xButton(d.buttonNum.toString())!,
                    )
                    .attr("y", y(0)!)
                    .attr("height", 0)
                    .call(updateBar),
            (update) => update.call(updateBar),
            (remove) => remove.call((remove) => remove.transition(trans).attr("height", 0).attr("y", y(0)!)),
        );

    // hover/tooltip

    function tooltipText(d: Datum): string {
        const button = tr.statisticsAnswerButtonsButtonNumber();
        const timesPressed = tr.statisticsAnswerButtonsButtonPressed();
        const correctStr = tr.statisticsHoursCorrect(totalCorrect(d.group));
        const pressedStr = `${timesPressed}: ${totalPressedStr(d)}`;
        return `${button}: ${d.buttonNum}<br>${pressedStr}<br>${correctStr}`;
    }

    svg.select("g.hover-columns")
        .selectAll("rect")
        .data(data)
        .join("rect")
        .attr("x", (d: Datum) => xGroup(d.group)! + xButton(d.buttonNum.toString())!)
        .attr("y", () => y(yMax!)!)
        .attr("width", xButton.bandwidth())
        .attr("height", () => y(0)! - y(yMax!)!)
        .on("mousemove", (event: MouseEvent, d: Datum) => {
            const [x, y] = pointer(event, document.body);
            showTooltip(tooltipText(d), x, y);
        })
        .on("mouseout", hideTooltip);
}
