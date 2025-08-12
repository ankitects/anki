// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/* eslint
@typescript-eslint/no-explicit-any: "off",
 */

import type { GraphsResponse } from "@generated/anki/stats_pb";
import * as tr from "@generated/ftl";
import { localizedNumber } from "@tslib/i18n";
import {
    arc,
    cumsum,
    interpolate,
    pie,
    scaleLinear,
    schemeBlues,
    schemeGreens,
    schemeOranges,
    schemeReds,
    select,
    sum,
} from "d3";

import { colorBlindColors, type GraphBounds } from "./graph-helpers";

type Count = [string, number, boolean, string];
export interface GraphData {
    title: string;
    counts: Count[];
    totalCards: string;
}

let barColours;

if((window as any).colorBlindMode)
{
    barColours = [
    colorBlindColors.new, /* new */
    colorBlindColors.learn, /* learn */
    colorBlindColors.relearn, /* relearn */
    colorBlindColors.young, /* young */
    colorBlindColors.mature, /* mature */
    colorBlindColors.suspended, /* suspended */
    colorBlindColors.buried, /* buried */
    ];
}
else 
{
    barColours = [
    schemeBlues[5][2], /* new */
    schemeOranges[5][2], /* learn */
    schemeReds[5][2], /* relearn */
    schemeGreens[5][2], /* young */
    schemeGreens[5][3], /* mature */
    "#FFDC41", /* suspended */
    "#grey", /* buried */
    ];
}

function countCards(data: GraphsResponse, separateInactive: boolean): Count[] {
    const countData = separateInactive ? data.cardCounts!.excludingInactive! : data.cardCounts!.includingInactive!;

    const extraQuery = separateInactive ? "AND -(\"is:buried\" OR \"is:suspended\")" : "";

    const counts: Count[] = [
        [tr.statisticsCountsNewCards(), countData.newCards, true, `"is:new"${extraQuery}`],
        [
            tr.statisticsCountsLearningCards(),
            countData.learn,
            true,
            `(-"is:review" AND "is:learn")${extraQuery}`,
        ],
        [
            tr.statisticsCountsRelearningCards(),
            countData.relearn,
            true,
            `("is:review" AND "is:learn")${extraQuery}`,
        ],
        [
            tr.statisticsCountsYoungCards(),
            countData.young,
            true,
            `("is:review" AND -"is:learn") AND "prop:ivl<21"${extraQuery}`,
        ],
        [
            tr.statisticsCountsMatureCards(),
            countData.mature,
            true,
            `("is:review" -"is:learn") AND "prop:ivl>=21"${extraQuery}`,
        ],
        [
            tr.statisticsCountsSuspendedCards(),
            countData.suspended,
            separateInactive,
            "\"is:suspended\"",
        ],
        [tr.statisticsCountsBuriedCards(), countData.buried, separateInactive, "\"is:buried\""],
    ];

    return counts;
}

export function gatherData(
    data: GraphsResponse,
    separateInactive: boolean,
): GraphData {
    const counts = countCards(data, separateInactive);
    const totalCards = localizedNumber(sum(counts, e => e[1]));

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
    count: string;
    query: string;
    percent: string;
    colour: string;
}

export function renderCards(
    svgElem: SVGElement,
    bounds: GraphBounds,
    sourceData: GraphData,
): TableDatum[] {
    const summed = cumsum(sourceData.counts, (d: Count) => d[1]);
    const data = Array.from(summed).map((n, idx) => {
        const count = sourceData.counts[idx];
        return {
            label: count[0],
            count: count[1],
            show: count[2],
            query: count[3],
            total: n,
        } satisfies SummedDatum;
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
            function(update) {
                return update.call((d) =>
                    d.transition(trans).attrTween("d", (d) => {
                        const interpolator = interpolate(
                            { startAngle: 0, endAngle: 0 },
                            d,
                        );
                        return (t): string => arcGen(interpolator(t) as any) as string;
                    })
                );
            },
        );

    x.range([bounds.marginLeft, bounds.width - bounds.marginRight]);

    const tableData = data.flatMap((d: SummedDatum, idx: number) => {
        const percent = localizedNumber((d.count / xMax) * 100, 2);
        return d.show
            ? ({
                label: d.label,
                count: localizedNumber(d.count),
                percent: `${percent}%`,
                colour: barColours[idx],
                query: d.query,
            } satisfies TableDatum)
            : [];
    });

    return tableData;
}
