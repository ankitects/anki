// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/* eslint
@typescript-eslint/no-non-null-assertion: "off",
@typescript-eslint/no-explicit-any: "off",
 */

import { CardQueue, CardType } from "lib/cards";
import type { Stats, Cards } from "lib/proto";
import {
    schemeGreens,
    schemeBlues,
    schemeOranges,
    schemeReds,
    select,
    scaleLinear,
    pie,
    arc,
    interpolate,
    cumsum,
} from "d3";
import type { GraphBounds } from "./graph-helpers";

import * as tr from "lib/i18n";

type Count = [string, number, boolean, string];
export interface GraphData {
    title: string;
    counts: Count[];
    totalCards: number;
}

const barColours = [
    schemeBlues[5][2] /* new */,
    schemeOranges[5][2] /* learn */,
    schemeReds[5][2] /* relearn */,
    schemeGreens[5][2] /* young */,
    schemeGreens[5][3] /* mature */,
    "#FFDC41" /* suspended */,
    "grey" /* buried */,
];

function countCards(cards: Cards.ICard[], separateInactive: boolean): Count[] {
    let newCards = 0;
    let learn = 0;
    let relearn = 0;
    let young = 0;
    let mature = 0;
    let suspended = 0;
    let buried = 0;

    for (const card of cards as Cards.Card[]) {
        if (separateInactive) {
            switch (card.queue) {
                case CardQueue.Suspended:
                    suspended += 1;
                    continue;
                case CardQueue.SchedBuried:
                case CardQueue.UserBuried:
                    buried += 1;
                    continue;
            }
        }

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
                } else {
                    mature += 1;
                }
                break;
            case CardType.Relearn:
                relearn += 1;
                break;
        }
    }

    const extraQuery = separateInactive ? 'AND -("is:buried" OR "is:suspended")' : "";

    const counts: Count[] = [
        [tr.statisticsCountsNewCards(), newCards, true, `"is:new"${extraQuery}`],
        [
            tr.statisticsCountsLearningCards(),
            learn,
            true,
            `(-"is:review" AND "is:learn")${extraQuery}`,
        ],
        [
            tr.statisticsCountsRelearningCards(),
            relearn,
            true,
            `("is:review" AND "is:learn")${extraQuery}`,
        ],
        [
            tr.statisticsCountsYoungCards(),
            young,
            true,
            `("is:review" AND -"is:learn") AND "prop:ivl<21"${extraQuery}`,
        ],
        [
            tr.statisticsCountsMatureCards(),
            mature,
            true,
            `("is:review" -"is:learn") AND "prop:ivl>=21"${extraQuery}`,
        ],
        [
            tr.statisticsCountsSuspendedCards(),
            suspended,
            separateInactive,
            '"is:suspended"',
        ],
        [tr.statisticsCountsBuriedCards(), buried, separateInactive, '"is:buried"'],
    ];

    return counts;
}

export function gatherData(
    data: Stats.GraphsResponse,
    separateInactive: boolean
): GraphData {
    const totalCards = data.cards.length;
    const counts = countCards(data.cards, separateInactive);

    return {
        title: tr.statisticsCountsTitle(),
        counts,
        totalCards,
    };
}

export interface SummedDatum {
    label: string;
    // count of this particular item
    count: number;
    // show up in the table
    show: boolean;
    query: string;
    // running total
    total: number;
}

export interface TableDatum {
    label: string;
    count: number;
    query: string;
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
            show: count[2],
            query: count[3],
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
                    .attr("fill", (_d, idx) => {
                        return barColours[idx];
                    })
                    .attr("d", arcGen as any),
            function (update) {
                return update.call((d) =>
                    d.transition(trans).attrTween("d", (d) => {
                        const interpolator = interpolate(
                            { startAngle: 0, endAngle: 0 },
                            d
                        );
                        return (t): string => arcGen(interpolator(t) as any) as string;
                    })
                );
            }
        );

    x.range([bounds.marginLeft, bounds.width - bounds.marginRight]);

    const tableData = data.flatMap((d: SummedDatum, idx: number) => {
        const percent = ((d.count / xMax) * 100).toFixed(1);
        return d.show
            ? ({
                  label: d.label,
                  count: d.count,
                  percent: `${percent}%`,
                  colour: barColours[idx],
                  query: d.query,
              } as TableDatum)
            : [];
    });

    return tableData;
}
