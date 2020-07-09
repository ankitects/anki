// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/* eslint
@typescript-eslint/no-non-null-assertion: "off",
@typescript-eslint/no-explicit-any: "off",
 */

import { CardQueue } from "../cards";
import pb from "../backend/proto";
import { schemeGreens, schemeBlues } from "d3-scale-chromatic";
import "d3-transition";
import { select, mouse } from "d3-selection";
import { scaleLinear } from "d3-scale";
import { showTooltip, hideTooltip } from "./tooltip";
import { GraphBounds } from "./graphs";
import { cumsum } from "d3-array";
import { I18n } from "../i18n";

type Count = [string, number];
export interface GraphData {
    title: string;
    counts: Count[];
    totalCards: number;
}

export function gatherData(data: pb.BackendProto.GraphsOut, i18n: I18n): GraphData {
    // fixme: handle preview cards
    const totalCards = data.cards.length;
    let newCards = 0;
    let young = 0;
    let mature = 0;
    let suspended = 0;
    let buried = 0;

    for (const card of data.cards as pb.BackendProto.Card[]) {
        switch (card.queue) {
            case CardQueue.New:
                newCards += 1;
                break;
            case CardQueue.Review:
                if (card.ivl >= 21) {
                    mature += 1;
                    break;
                }
            // young falls through
            case CardQueue.Learn:
            case CardQueue.DayLearn:
                young += 1;
                break;
            case CardQueue.Suspended:
                suspended += 1;
                break;
            case CardQueue.SchedBuried:
            case CardQueue.UserBuried:
                buried += 1;
                break;
        }
    }

    const counts = [
        [i18n.tr(i18n.TR.STATISTICS_COUNTS_NEW_CARDS), newCards] as Count,
        [i18n.tr(i18n.TR.STATISTICS_COUNTS_YOUNG_CARDS), young] as Count,
        [i18n.tr(i18n.TR.STATISTICS_COUNTS_MATURE_CARDS), mature] as Count,
        [i18n.tr(i18n.TR.STATISTICS_COUNTS_SUSPENDED_CARDS), suspended] as Count,
        [i18n.tr(i18n.TR.STATISTICS_COUNTS_BURIED_CARDS), buried] as Count,
    ];

    return {
        title: i18n.tr(i18n.TR.STATISTICS_COUNTS_TITLE),
        counts,
        totalCards,
    };
}

interface Reviews {
    mature: number;
    young: number;
    learn: number;
    relearn: number;
    early: number;
}

export function renderCards(
    svgElem: SVGElement,
    bounds: GraphBounds,
    sourceData: GraphData
): void {
    const summed = cumsum(sourceData.counts, (d) => d[1]);
    const data = Array.from(summed).map((n, idx) => {
        return {
            count: sourceData.counts[idx],
            idx,
            total: n,
        };
    });
    // ensuring a non-zero range makes a better animation
    // in the empty data case
    const xMax = Math.max(1, summed.slice(-1)[0]);
    const x = scaleLinear().domain([0, xMax]);
    const svg = select(svgElem);
    const trans = svg.transition().duration(600) as any;

    x.range([bounds.marginLeft, bounds.width - bounds.marginRight - bounds.marginLeft]);

    const tooltipText = (d: any): string => {
        const pct = ((d.count[1] / xMax) * 100).toFixed(2);
        return `${d.count[0]}: ${d.count[1]} (${pct}%)`;
    };

    const updateBar = (sel: any): any => {
        return sel
            .on("mousemove", function (this: any, d: any) {
                const [x, y] = mouse(document.body);
                showTooltip(tooltipText(d), x, y);
            })
            .transition(trans)
            .attr("x", (d) => x(d.total - d.count[1]))
            .attr("width", (d) => x(d.count[1]) - x(0));
    };

    svg.select("g.days")
        .selectAll("rect")
        .data(data)
        .join(
            (enter) =>
                enter
                    .append("rect")
                    .attr("height", 10)
                    .attr("y", bounds.marginTop)
                    .attr("fill", (d: any): any => {
                        switch (d.idx) {
                            case 0:
                                return schemeBlues[5][2];
                            case 1:
                                return schemeGreens[5][2];
                            case 2:
                                return schemeGreens[5][3];
                            case 3:
                                return "#FFDC41";
                            case 4:
                                return "grey";
                        }
                    })
                    .on("mouseout", hideTooltip)

                    .call((d) => updateBar(d)),
            (update) => update.call((d) => updateBar(d))
        );
}
