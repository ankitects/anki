// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/* eslint
@typescript-eslint/no-non-null-assertion: "off",
@typescript-eslint/no-explicit-any: "off",
 */

import pb from "anki/backend_proto";
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
import type { I18n } from "anki/i18n";

type ButtonCounts = [number, number, number, number];

export interface GraphData {
    learning: ButtonCounts;
    young: ButtonCounts;
    mature: ButtonCounts;
}

const ReviewKind = pb.BackendProto.RevlogEntry.ReviewKind;

export function gatherData(
    data: pb.BackendProto.GraphsOut,
    range: GraphRange
): GraphData {
    const cutoff = millisecondCutoffForRange(range, data.nextDayAtSecs);
    const learning: ButtonCounts = [0, 0, 0, 0];
    const young: ButtonCounts = [0, 0, 0, 0];
    const mature: ButtonCounts = [0, 0, 0, 0];

    for (const review of data.revlog as pb.BackendProto.RevlogEntry[]) {
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
    origData: pb.BackendProto.GraphsOut,
    i18n: I18n,
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
        .transition(trans)
        .call(
            axisBottom(xGroup)
                .tickFormat(((d: GroupKind) => {
                    let kind: string;
                    switch (d) {
                        case "learning":
                            kind = i18n.tr(i18n.TR.STATISTICS_COUNTS_LEARNING_CARDS);
                            break;
                        case "young":
                            kind = i18n.tr(i18n.TR.STATISTICS_COUNTS_YOUNG_CARDS);
                            break;
                        case "mature":
                        default:
                            kind = i18n.tr(i18n.TR.STATISTICS_COUNTS_MATURE_CARDS);
                            break;
                    }
                    return `${kind} \u200e(${totalCorrect(d).percent}%)`;
                }) as any)
                .tickSizeOuter(0)
        );

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
        .transition(trans)
        .call(
            axisLeft(y)
                .ticks(bounds.height / 50)
                .tickSizeOuter(0)
        );

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
        const button = i18n.tr(i18n.TR.STATISTICS_ANSWER_BUTTONS_BUTTON_NUMBER);
        const timesPressed = i18n.tr(i18n.TR.STATISTICS_ANSWER_BUTTONS_BUTTON_PRESSED);
        const correctStr = i18n.tr(
            i18n.TR.STATISTICS_HOURS_CORRECT,
            totalCorrect(d.group)
        );
        return `${button}: ${d.buttonNum}<br>${timesPressed}: ${d.count}<br>${correctStr}`;
    }

    svg.select("g.hoverzone")
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
