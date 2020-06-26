// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/* eslint
@typescript-eslint/no-non-null-assertion: "off",
@typescript-eslint/no-explicit-any: "off",
 */

import pb from "../backend/proto";
import { interpolateRdYlGn } from "d3-scale-chromatic";
import "d3-transition";
import { select, mouse } from "d3-selection";
import { scaleLinear, scaleBand, scaleSequential } from "d3-scale";
import { axisBottom, axisLeft } from "d3-axis";
import { showTooltip, hideTooltip } from "./tooltip";
import { GraphBounds } from "./graphs";

type ButtonCounts = [number, number, number, number];

export interface GraphData {
    learning: ButtonCounts;
    young: ButtonCounts;
    mature: ButtonCounts;
}

const ReviewKind = pb.BackendProto.RevlogEntry.ReviewKind;

export function gatherData(data: pb.BackendProto.GraphsOut): GraphData {
    const learning: ButtonCounts = [0, 0, 0, 0];
    const young: ButtonCounts = [0, 0, 0, 0];
    const mature: ButtonCounts = [0, 0, 0, 0];

    for (const review of data.revlog as pb.BackendProto.RevlogEntry[]) {
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

interface Datum {
    buttonNum: string;
    group: "learning" | "young" | "mature";
    count: number;
}

function tooltipText(d: Datum): string {
    return JSON.stringify(d);
}

export function renderButtons(
    svgElem: SVGElement,
    bounds: GraphBounds,
    sourceData: GraphData
): void {
    const data = [
        ...sourceData.learning.map((count: number, idx: number) => {
            return {
                buttonNum: (idx + 1).toString(),
                group: "learning",
                count,
            } as Datum;
        }),
        ...sourceData.young.map((count: number, idx: number) => {
            return {
                buttonNum: (idx + 1).toString(),
                group: "young",
                count,
            } as Datum;
        }),
        ...sourceData.mature.map((count: number, idx: number) => {
            return {
                buttonNum: (idx + 1).toString(),
                group: "mature",
                count,
            } as Datum;
        }),
    ];

    const yMax = Math.max(...data.map((d) => d.count));

    const svg = select(svgElem);
    const trans = svg.transition().duration(600) as any;

    const xGroup = scaleBand()
        .domain(["learning", "young", "mature"])
        .range([bounds.marginLeft, bounds.width - bounds.marginRight]);
    svg.select<SVGGElement>(".x-ticks").transition(trans).call(
        axisBottom(xGroup)
            // .ticks()
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
                .ticks(bounds.height / 80)
                .tickSizeOuter(0)
        );

    // x bars

    const updateBar = (sel: any): any => {
        return sel
            .attr("width", xButton.bandwidth())
            .attr("opacity", (d: Datum) => {
                switch (d.group) {
                    case "learning":
                        return 0.3;
                    case "young":
                        return 0.5;
                    case "mature":
                        return 1;
                }
            })
            .transition(trans)
            .attr("x", (d: Datum) => xGroup(d.group)! + xButton(d.buttonNum)!)
            .attr("y", (d: Datum) => y(d.count)!)
            .attr("height", (d: Datum) => y(0) - y(d.count))
            .attr("fill", (d: Datum) => colour(parseInt(d.buttonNum)));
    };

    svg.select("g.bars")
        .selectAll("rect")
        .data(data)
        .join(
            (enter) =>
                enter
                    .append("rect")
                    .attr("rx", 1)
                    .attr("x", (d: Datum) => xGroup(d.group)! + xButton(d.buttonNum)!)
                    .attr("y", y(0))
                    .attr("height", 0)
                    .call(updateBar),
            (update) => update.call(updateBar),
            (remove) =>
                remove.call((remove) =>
                    remove.transition(trans).attr("height", 0).attr("y", y(0))
                )
        );

    // hover/tooltip
    svg.select("g.hoverzone")
        .selectAll("rect")
        .data(data)
        .join("rect")
        .attr("x", (d: Datum) => xGroup(d.group)! + xButton(d.buttonNum)!)
        .attr("y", () => y(yMax!))
        .attr("width", xButton.bandwidth())
        .attr("height", () => y(0) - y(yMax!))
        .on("mousemove", function (this: any, d: Datum) {
            const [x, y] = mouse(document.body);
            showTooltip(tooltipText(d), x, y);
        })
        .on("mouseout", hideTooltip);
}
