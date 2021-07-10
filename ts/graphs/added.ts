// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/* eslint
@typescript-eslint/no-non-null-assertion: "off",
@typescript-eslint/no-explicit-any: "off",
 */

import type { Stats, Cards } from "lib/proto";

import {
    extent,
    histogram,
    sum,
    scaleLinear,
    scaleSequential,
    interpolateBlues,
} from "d3";
import type { Bin } from "d3";
import type { HistogramData } from "./histogram-graph";

import { dayLabel } from "lib/time";
import { GraphRange } from "./graph-helpers";
import type { TableDatum, SearchDispatch } from "./graph-helpers";
import * as tr from "lib/i18n";

export interface GraphData {
    daysAdded: number[];
}

export function gatherData(data: Stats.GraphsResponse): GraphData {
    const daysAdded = (data.cards as Cards.Card[]).map((card) => {
        const elapsedSecs = (card.id as number) / 1000 - data.nextDayAtSecs;
        return Math.ceil(elapsedSecs / 86400);
    });
    return { daysAdded };
}

function makeQuery(start: number, end: number): string {
    const include = `"added:${start}"`;

    if (start === 1) {
        return include;
    }

    const exclude = `-"added:${end}"`;
    return `${include} AND ${exclude}`;
}

export function buildHistogram(
    data: GraphData,
    range: GraphRange,
    dispatch: SearchDispatch,
    browserLinksSupported: boolean
): [HistogramData | null, TableDatum[]] {
    // get min/max
    const total = data.daysAdded.length;
    if (!total) {
        return [null, []];
    }

    const [xMinOrig] = extent(data.daysAdded);
    let xMin = xMinOrig;

    // cap max to selected range
    switch (range) {
        case GraphRange.Month:
            xMin = -31;
            break;
        case GraphRange.ThreeMonths:
            xMin = -90;
            break;
        case GraphRange.Year:
            xMin = -365;
            break;
        case GraphRange.AllTime:
            break;
    }
    const xMax = 1;
    const desiredBars = Math.min(70, Math.abs(xMin!));

    const scale = scaleLinear().domain([xMin!, xMax]);
    const bins = histogram()
        .domain(scale.domain() as any)
        .thresholds(scale.ticks(desiredBars))(data.daysAdded);

    // empty graph?
    if (!sum(bins, (bin) => bin.length)) {
        return [null, []];
    }

    const adjustedRange = scaleLinear().range([0.7, 0.3]);
    const colourScale = scaleSequential((n) =>
        interpolateBlues(adjustedRange(n)!)
    ).domain([xMax!, xMin!]);

    const totalInPeriod = sum(bins, (bin) => bin.length);
    const periodDays = Math.abs(xMin!);
    const cardsPerDay = Math.round(totalInPeriod / periodDays);
    const tableData = [
        {
            label: tr.statisticsTotal(),
            value: tr.statisticsCards({ cards: totalInPeriod }),
        },
        {
            label: tr.statisticsAverage(),
            value: tr.statisticsCardsPerDay({ count: cardsPerDay }),
        },
    ];

    function hoverText(
        bin: Bin<number, number>,
        cumulative: number,
        _percent: number
    ): string {
        const day = dayLabel(bin.x0!, bin.x1!);
        const cards = tr.statisticsCards({ cards: bin.length });
        const total = tr.statisticsRunningTotal();
        const totalCards = tr.statisticsCards({ cards: cumulative });
        return `${day}:<br>${cards}<br>${total}: ${totalCards}`;
    }

    function onClick(bin: Bin<number, number>): void {
        const start = Math.abs(bin.x0!) + 1;
        const end = Math.abs(bin.x1!) + 1;
        const query = makeQuery(start, end);
        dispatch("search", { query });
    }

    return [
        {
            scale,
            bins,
            total: totalInPeriod,
            hoverText,
            onClick: browserLinksSupported ? onClick : null,
            colourScale,
            showArea: true,
        },
        tableData,
    ];
}
