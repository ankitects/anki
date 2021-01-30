// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/* eslint
@typescript-eslint/no-non-null-assertion: "off",
@typescript-eslint/no-explicit-any: "off",
 */

import type pb from "anki/backend_proto";

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
import type { I18n } from "anki/i18n";
import { dayLabel } from "anki/time";
import { GraphRange } from "./graph-helpers";
import type { TableDatum, SearchDispatch } from "./graph-helpers";

export interface GraphData {
    daysAdded: number[];
}

export function gatherData(data: pb.BackendProto.GraphsOut): GraphData {
    const daysAdded = (data.cards as pb.BackendProto.Card[]).map((card) => {
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
    i18n: I18n,
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
            label: i18n.tr(i18n.TR.STATISTICS_TOTAL),
            value: i18n.tr(i18n.TR.STATISTICS_CARDS, { cards: totalInPeriod }),
        },
        {
            label: i18n.tr(i18n.TR.STATISTICS_AVERAGE),
            value: i18n.tr(i18n.TR.STATISTICS_CARDS_PER_DAY, { count: cardsPerDay }),
        },
    ];

    function hoverText(
        bin: Bin<number, number>,
        cumulative: number,
        _percent: number
    ): string {
        const day = dayLabel(i18n, bin.x0!, bin.x1!);
        const cards = i18n.tr(i18n.TR.STATISTICS_CARDS, { cards: bin.length });
        const total = i18n.tr(i18n.TR.STATISTICS_RUNNING_TOTAL);
        const totalCards = i18n.tr(i18n.TR.STATISTICS_CARDS, { cards: cumulative });
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
