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
    quantile,
    sum,
    mean,
    scaleLinear,
    scaleSequential,
    interpolateBlues,
} from "d3";
import type { Bin } from "d3";
import { CardType } from "anki/cards";
import type { HistogramData } from "./histogram-graph";
import type { I18n } from "anki/i18n";
import type { TableDatum, SearchDispatch } from "./graph-helpers";
import { timeSpan } from "anki/time";

export interface IntervalGraphData {
    intervals: number[];
}

export enum IntervalRange {
    Month = 0,
    Percentile50 = 1,
    Percentile95 = 2,
    All = 3,
}

export function gatherIntervalData(data: pb.BackendProto.GraphsOut): IntervalGraphData {
    const intervals = (data.cards as pb.BackendProto.Card[])
        .filter((c) => [CardType.Review, CardType.Relearn].includes(c.ctype))
        .map((c) => c.interval);
    return { intervals };
}

export function intervalLabel(
    i18n: I18n,
    daysStart: number,
    daysEnd: number,
    cards: number
): string {
    if (daysEnd - daysStart <= 1) {
        // singular
        return i18n.tr(i18n.TR.STATISTICS_INTERVALS_DAY_SINGLE, {
            day: daysStart,
            cards,
        });
    } else {
        // range
        return i18n.tr(i18n.TR.STATISTICS_INTERVALS_DAY_RANGE, {
            daysStart,
            daysEnd: daysEnd - 1,
            cards,
        });
    }
}

function makeQuery(start: number, end: number): string {
    if (start === end) {
        return `"prop:ivl=${start}"`;
    }

    const fromQuery = `"prop:ivl>=${start}"`;
    const tillQuery = `"prop:ivl<=${end}"`;

    return `${fromQuery} AND ${tillQuery}`;
}

export function prepareIntervalData(
    data: IntervalGraphData,
    range: IntervalRange,
    i18n: I18n,
    dispatch: SearchDispatch,
    browserLinksSupported: boolean
): [HistogramData | null, TableDatum[]] {
    // get min/max
    const allIntervals = data.intervals;
    if (!allIntervals.length) {
        return [null, []];
    }

    const xMin = 0;
    let [, xMax] = extent(allIntervals);
    let niceNecessary = false;

    // cap max to selected range
    switch (range) {
        case IntervalRange.Month:
            xMax = Math.min(xMax!, 30);
            break;
        case IntervalRange.Percentile50:
            xMax = quantile(allIntervals, 0.5);
            niceNecessary = true;
            break;
        case IntervalRange.Percentile95:
            xMax = quantile(allIntervals, 0.95);
            niceNecessary = true;
            break;
        case IntervalRange.All:
            niceNecessary = true;
            break;
    }

    xMax = xMax! + 1;

    // do not show the zero interval
    const increment = (x: number): number => x + 1;

    const adjustTicks = (x: number, idx: number, ticks: number[]): number[] =>
        idx === ticks.length - 1 ? [x - (ticks[0] - 1), x + 1] : [x - (ticks[0] - 1)];

    // cap bars to available range
    const desiredBars = Math.min(70, xMax! - xMin!);

    const prescale = scaleLinear().domain([xMin!, xMax!]);
    const scale = scaleLinear().domain(
        (niceNecessary ? prescale.nice() : prescale).domain().map(increment)
    );

    const bins = histogram()
        .domain(scale.domain() as [number, number])
        .thresholds(scale.ticks(desiredBars).flatMap(adjustTicks))(allIntervals);

    // empty graph?
    const totalInPeriod = sum(bins, (bin) => bin.length);
    if (!totalInPeriod) {
        return [null, []];
    }

    const adjustedRange = scaleLinear().range([0.7, 0.3]);
    const colourScale = scaleSequential((n) =>
        interpolateBlues(adjustedRange(n)!)
    ).domain([xMax!, xMin!]);

    function hoverText(
        bin: Bin<number, number>,
        _cumulative: number,
        percent: number
    ): string {
        // const day = dayLabel(i18n, bin.x0!, bin.x1!);
        const interval = intervalLabel(i18n, bin.x0!, bin.x1!, bin.length);
        const total = i18n.tr(i18n.TR.STATISTICS_RUNNING_TOTAL);
        return `${interval}<br>${total}: \u200e${percent.toFixed(1)}%`;
    }

    function onClick(bin: Bin<number, number>): void {
        const start = bin.x0!;
        const end = bin.x1! - 1;
        const query = makeQuery(start, end);
        dispatch("search", { query });
    }

    const meanInterval = Math.round(mean(allIntervals) ?? 0);
    const meanIntervalString = timeSpan(i18n, meanInterval * 86400, false);
    const tableData = [
        {
            label: i18n.tr(i18n.TR.STATISTICS_AVERAGE_INTERVAL),
            value: meanIntervalString,
        },
    ];

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
