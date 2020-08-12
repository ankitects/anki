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
import { select } from "d3-selection";
import { scaleLinear } from "d3-scale";
import { pie, arc } from "d3-shape";
import { interpolate } from "d3-interpolate";
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
            case CardQueue.PreviewRepeat:
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

function barColour(idx: number): string {
    switch (idx) {
        case 0:
            return schemeBlues[5][2];
        case 1:
            return schemeGreens[5][2];
        case 2:
            return schemeGreens[5][3];
        case 3:
            return "#FFDC41";
        case 4:
        default:
            return "grey";
    }
}

export interface SummedDatum {
    label: string;
    // count of this particular item
    count: number;
    idx: number;
    // running total
    total: number;
}

export interface TableDatum {
    label: string;
    count: number;
    percent: string;
    colour: string;
}

export function renderCards(
    svgElem: SVGElement,
    bounds: GraphBounds,
    sourceData: GraphData
): TableDatum[] {
    const summed = cumsum(sourceData.counts, (d) => d[1]);
    const data = Array.from(summed).map((n, idx) => {
        const count = sourceData.counts[idx];
        return {
            label: count[0],
            count: count[1],
            idx,
            total: n,
        } as SummedDatum;
    });
    // ensuring a non-zero range makes the percentages not break
    // in an empty collection
    const xMax = Math.max(1, summed.slice(-1)[0]);
    const x = scaleLinear().domain([0, xMax]);
    const svg = select(svgElem);
    const paths = svg.select(".counts");
    const pieData = pie()(sourceData.counts.map((d) => d[1]));
    const radius = bounds.height / 2 - bounds.marginTop - bounds.marginBottom;
    const arcGen = arc().innerRadius(0).outerRadius(radius);
    const trans = svg.transition().duration(600) as any;

    paths
        .attr("transform", `translate(${radius},${radius + bounds.marginTop})`)
        .selectAll("path")
        .data(pieData)
        .join(
            (enter) =>
                enter
                    .append("path")
                    .attr("fill", function (d, i) {
                        return barColour(i);
                    })
                    .attr("d", arcGen as any),
            function (update) {
                return update.call((d) =>
                    d.transition(trans).attrTween("d", (d) => {
                        const interpolator = interpolate(
                            { startAngle: 0, endAngle: 0 },
                            d
                        );
                        return (t): string => arcGen(interpolator(t)) as string;
                    })
                );
            }
        );

    x.range([bounds.marginLeft, bounds.width - bounds.marginRight]);

    const tableData = data.map((d, idx) => {
        const percent = ((d.count / xMax) * 100).toFixed(1);
        return {
            label: d.label,
            count: d.count,
            percent: `${percent}%`,
            colour: barColour(idx),
        } as TableDatum;
    });

    return tableData;
}
