// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/* eslint
@typescript-eslint/no-non-null-assertion: "off",
@typescript-eslint/no-explicit-any: "off",
 */

import { CardQueue, CardType } from "anki/cards";
import type pb from "anki/backend_proto";
import { schemeGreens, schemeBlues, schemeOranges} from "d3-scale-chromatic";
import "d3-transition";
import { select } from "d3-selection";
import { scaleLinear } from "d3-scale";
import { pie, arc } from "d3-shape";
import { interpolate } from "d3-interpolate";
import type { GraphBounds } from "./graph-helpers";
import { CardCountMethod } from "./graph-helpers";
import { cumsum } from "d3-array";
import type { I18n } from "anki/i18n";

type Count = [string, number, string];
export interface GraphData {
    title: string;
    counts: Count[];
    totalCards: number;
}

const barColours = {
    new: schemeBlues[5][2],
    review: schemeGreens[5][2],
    young: schemeGreens[5][2],
    mature: schemeGreens[5][3],
    learn: schemeOranges[5][2],
    relearn: schemeOranges[5][3],
    suspended: "#FFDC41",
    buried: "grey",
}

function gatherByQueue(cards: pb.BackendProto.ICard[], i18n: I18n): Count[] {
    let newCards = 0;
    let learn = 0;
    let review = 0;
    let suspended = 0;
    let buried = 0;

    for (const card of cards as pb.BackendProto.Card[]) {
        switch (card.queue) {
            case CardQueue.New:
                newCards += 1;
                break;
            case CardQueue.Review:
                review += 1;
                break
            case CardQueue.Learn:
            case CardQueue.DayLearn:
            case CardQueue.PreviewRepeat:
                learn += 1;
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

    const counts: Count[] = [
        [i18n.tr(i18n.TR.STATISTICS_COUNTS_NEW_CARDS), newCards, barColours.new],
        [i18n.tr(i18n.TR.STATISTICS_COUNTS_LEARNING_CARDS), learn, barColours.learn],
        ["Review", review, barColours.review],
        [i18n.tr(i18n.TR.STATISTICS_COUNTS_SUSPENDED_CARDS), suspended, barColours.suspended],
        [i18n.tr(i18n.TR.STATISTICS_COUNTS_BURIED_CARDS), buried, barColours.buried],
    ];

    return counts;
}

function gatherByCtype(cards: pb.BackendProto.ICard[], i18n: I18n): Count[] {
    let newCards = 0;
    let learn = 0;
    let young = 0;
    let mature = 0;
    let relearn = 0;

    for (const card of cards as pb.BackendProto.Card[]) {
        switch (card.ctype) {
            case CardType.New:
                newCards += 1;
                break;
            case CardType.Learn:
                learn += 1;
                break;
            case CardType.Review:
                if (card.interval < 21) {
                    young += 1;
                }
                else {
                    mature += 1;
                }
                break
            case CardType.Relearn:
                relearn += 1;
                break
        }
    }

    const counts: Count[] = [
        [i18n.tr(i18n.TR.STATISTICS_COUNTS_NEW_CARDS), newCards, barColours.new],
        [i18n.tr(i18n.TR.STATISTICS_COUNTS_LEARNING_CARDS), learn, barColours.learn],
        [i18n.tr(i18n.TR.STATISTICS_COUNTS_YOUNG_CARDS), young, barColours.young],
        [i18n.tr(i18n.TR.STATISTICS_COUNTS_MATURE_CARDS), mature, barColours.mature],
        [i18n.tr(i18n.TR.STATISTICS_COUNTS_RELEARNING_CARDS), relearn, barColours.relearn],
    ];

    return counts;
}

export function gatherData(data: pb.BackendProto.GraphsOut, method: CardCountMethod, i18n: I18n): GraphData {
    const totalCards = data.cards.length;
    const counts = method === CardCountMethod.ByType
        ?  gatherByCtype(data.cards, i18n)
        :  gatherByQueue(data.cards, i18n);

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

export interface SummedDatum {
    label: string;
    // count of this particular item
    count: number;
    colour: string;
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
    const summed = cumsum(sourceData.counts, (d: Count) => d[1]);
    const data = Array.from(summed).map((n, idx) => {
        const count = sourceData.counts[idx];
        return {
            label: count[0],
            count: count[1],
            colour: count[2],
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
    const pieData = pie()(sourceData.counts.map((d: Count) => d[1]));
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
                    .attr("fill", (_d, i) => {
                        return data[i].colour;
                    })
                    .attr("d", arcGen as any),
            function (update) {
                return update.call((d) =>
                    d
                    .transition(trans)
                    .attr("fill", (_d, i) => {
                        return data[i].colour;
                    })
                    .attrTween("d", (d) => {
                        const interpolator = interpolate(
                            { startAngle: 0, endAngle: 0 },
                            d,
                        );
                        return (t): string => arcGen(interpolator(t) as any) as string;
                    })
                );
            }
        );

    x.range([bounds.marginLeft, bounds.width - bounds.marginRight]);

    const tableData = data.map((d) => {
        const percent = ((d.count / xMax) * 100).toFixed(1);
        return {
            label: d.label,
            count: d.count,
            percent: `${percent}%`,
            colour: d.colour,
        } as TableDatum;
    });

    return tableData;
}
