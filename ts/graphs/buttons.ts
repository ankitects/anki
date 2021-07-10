// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/* eslint
@typescript-eslint/no-non-null-assertion: "off",
@typescript-eslint/no-explicit-any: "off",
 */

import { Stats } from "lib/proto";

import {
    interpolateRdYlGn,
    select,
    pointer,
    scaleLinear,
    scaleBand,
    scaleSequential,
    axisBottom,
    axisLeft,
    sum,
} from "d3";
import { showTooltip, hideTooltip } from "./tooltip";
import {
    GraphBounds,
    setDataAvailable,
    GraphRange,
    millisecondCutoffForRange,
} from "./graph-helpers";
import * as tr from "lib/i18n";

type ButtonCounts = [number, number, number, number];

export interface GraphData {
    learning: ButtonCounts;
    young: ButtonCounts;
    mature: ButtonCounts;
}

const ReviewKind = Stats.RevlogEntry.ReviewKind;

export function gatherData(data: Stats.GraphsResponse, range: GraphRange): GraphData {
    const cutoff = millisecondCutoffForRange(range, data.nextDayAtSecs);
    const learning: ButtonCounts = [0, 0, 0, 0];
    const young: ButtonCounts = [0, 0, 0, 0];
    const mature: ButtonCounts = [0, 0, 0, 0];

    for (const review of data.revlog as Stats.RevlogEntry[]) {
        if (cutoff && (review.id as number) < cutoff) {
            continue;
        }

        let buttonNum = review.buttonChosen;
        if (buttonNum <= 0 || buttonNum > 4) {
            continue;
        }

        let buttons = learning;
        switch (review.reviewKind) {
            case ReviewKind.LEARNING:
            case ReviewKind.RELEARNING:
                // V1 scheduler only had 3 buttons in learning
                if (buttonNum === 4 && data.schedulerVersion === 1) {
                    buttonNum = 3;
                }
                break;

            case ReviewKind.REVIEW:
            case ReviewKind.EARLY_REVIEW:
                if (review.lastInterval < 21) {
                    buttons = young;
                } else {
                    buttons = mature;
                }
                break;
        }

        buttons[buttonNum - 1] += 1;
    }
    return { learning, young, mature };
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
    origData: Stats.GraphsResponse,
    range: GraphRange
): void {
    const sourceData = gatherData(origData, range);
    const data = [
        ...sourceData.learning.map((count: number, idx: number) => {
            return {
                buttonNum: idx + 1,
                group: "learning",
                count,
            } as Datum;
        }),
        ...sourceData.young.map((count: number, idx: number) => {
            return {
                buttonNum: idx + 1,
                group: "young",
                count,
            } as Datum;
        }),
        ...sourceData.mature.map((count: number, idx: number) => {
            return {
                buttonNum: idx + 1,
                group: "mature",
                count,
            } as Datum;
        }),
    ];

    const totalCorrect = (kind: GroupKind): TotalCorrect => {
        const groupData = data.filter((d) => d.group == kind);
        const total = sum(groupData, (d) => d.count);
        const correct = sum(
            groupData.filter((d) => d.buttonNum > 1),
            (d) => d.count
        );
        const percent = total ? ((correct / total) * 100).toFixed(2) : "0";
        return { total, correct, percent };
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
                    .tickFormat(((d: GroupKind) => {
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
                        return `${kind} \u200e(${totalCorrect(d).percent}%)`;
                    }) as any)
                    .tickSizeOuter(0)
            )
        )
        .attr("direction", "ltr");

    const xButton = scaleBand()
        .domain(["1", "2", "3", "4"])
        .range([0, xGroup.bandwidth()])
        .paddingOuter(1)
        .paddingInner(0.1);

    const colour = scaleSequential(interpolateRdYlGn).domain([1, 4]);

    // y scale

    const y = scaleLinear()
        .range([bounds.height - bounds.marginBottom, bounds.marginTop])
        .domain([0, yMax]);
    svg.select<SVGGElement>(".y-ticks")
        .call((selection) =>
            selection.transition(trans).call(
                axisLeft(y)
                    .ticks(bounds.height / 50)
                    .tickSizeOuter(0)
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
                (d: Datum) => xGroup(d.group)! + xButton(d.buttonNum.toString())!
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
                        (d: Datum) =>
                            xGroup(d.group)! + xButton(d.buttonNum.toString())!
                    )
                    .attr("y", y(0)!)
                    .attr("height", 0)
                    .call(updateBar),
            (update) => update.call(updateBar),
            (remove) =>
                remove.call((remove) =>
                    remove.transition(trans).attr("height", 0).attr("y", y(0)!)
                )
        );

    // hover/tooltip

    function tooltipText(d: Datum): string {
        const button = tr.statisticsAnswerButtonsButtonNumber();
        const timesPressed = tr.statisticsAnswerButtonsButtonPressed();
        const correctStr = tr.statisticsHoursCorrect(totalCorrect(d.group));
        return `${button}: ${d.buttonNum}<br>${timesPressed}: ${d.count}<br>${correctStr}`;
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
