// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/* eslint
@typescript-eslint/no-explicit-any: "off",
 */

import type { GraphsResponse_Intervals } from "@generated/anki/stats_pb";
import * as tr from "@generated/ftl";
import { localizedNumber } from "@tslib/i18n";
import { timeSpan } from "@tslib/time";
import type { Bin } from "d3";
import { bin, extent, interpolateBlues, mean, quantile, scaleLinear, scaleSequential, sum } from "d3";

import type { SearchDispatch, TableDatum } from "./graph-helpers";
import { numericMap } from "./graph-helpers";
import type { HistogramData } from "./histogram-graph";

export interface IntervalGraphData {
    intervals: number[];
}

export enum IntervalRange {
    Month = 0,
    Percentile50 = 1,
    Percentile95 = 2,
    All = 3,
}

export function gatherIntervalData(data: GraphsResponse_Intervals): IntervalGraphData {
    // This could be made more efficient - this graph currently expects a flat list of individual intervals which it
    // uses to calculate a percentile and then converts into a histogram, and the percentile/histogram calculations
    // in JS are relatively slow.
    const map = numericMap(data.intervals);
    const totalCards = sum(map, ([_k, v]) => v);
    const allIntervals: number[] = Array(totalCards);
    let position = 0;
    for (const entry of map.entries()) {
        allIntervals.fill(entry[0], position, position + entry[1]);
        position += entry[1];
    }
    allIntervals.sort((a, b) => a - b);
    return { intervals: allIntervals };
}

export function intervalLabel(
    daysStart: number,
    daysEnd: number,
    cards: number,
    fsrs: boolean,
): string {
    if (daysEnd - daysStart <= 1) {
        // singular
        const fn = fsrs ? tr.statisticsStabilityDaySingle : tr.statisticsIntervalsDaySingle;
        return fn({
            day: daysStart,
            cards,
        });
    } else {
        // range
        const fn = fsrs ? tr.statisticsStabilityDayRange : tr.statisticsIntervalsDayRange;
        return fn({
            daysStart,
            daysEnd: daysEnd - 1,
            cards,
        });
    }
}

function makeSm2Query(start: number, end: number): string {
    if (start === end) {
        return `"prop:ivl=${start}"`;
    }

    const fromQuery = `"prop:ivl>=${start}"`;
    const tillQuery = `"prop:ivl<=${end}"`;
    return `${fromQuery} ${tillQuery}`;
}

function makeFsrsQuery(start: number, end: number): string {
    if (start === 0) {
        start = 0.5;
    }
    const fromQuery = `"prop:s>=${start - 0.5}"`;
    const tillQuery = `"prop:s<${end + 0.5}"`;
    return `${fromQuery} ${tillQuery}`;
}

export function prepareIntervalData(
    data: IntervalGraphData,
    range: IntervalRange,
    dispatch: SearchDispatch,
    browserLinksSupported: boolean,
    fsrs: boolean,
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

    // do not show the zero interval for intervals
    const increment = fsrs ? x => x : (x: number): number => x + 1;

    const adjustTicks = (x: number, idx: number, ticks: number[]): number[] =>
        idx === ticks.length - 1 ? [x - (ticks[0] - 1), x + 1] : [x - (ticks[0] - 1)];

    // cap bars to available range
    const desiredBars = Math.min(70, xMax! - xMin!);

    const prescale = scaleLinear().domain([xMin!, xMax!]);
    const scale = scaleLinear().domain(
        (niceNecessary ? prescale.nice() : prescale).domain().map(increment),
    );

    const bins = bin()
        .domain(scale.domain() as [number, number])
        .thresholds(scale.ticks(desiredBars).flatMap(adjustTicks))(allIntervals);

    // empty graph?
    const totalInPeriod = sum(bins, (bin) => bin.length);
    if (!totalInPeriod) {
        return [null, []];
    }

    const adjustedRange = scaleLinear().range([0.7, 0.3]);
    const colourScale = scaleSequential((n) => interpolateBlues(adjustedRange(n)!)).domain([xMax!, xMin!]);

    function hoverText(
        bin: Bin<number, number>,
        _cumulative: number,
        percent: number,
    ): string {
        // const day = dayLabel(bin.x0!, bin.x1!);
        const interval = intervalLabel(bin.x0!, bin.x1!, bin.length, fsrs);
        const total = tr.statisticsRunningTotal();
        return `${interval}<br>${total}: \u200e${localizedNumber(percent, 1)}%`;
    }

    function onClick(bin: Bin<number, number>): void {
        const start = bin.x0!;
        const end = bin.x1! - 1;
        const query = (fsrs ? makeFsrsQuery : makeSm2Query)(start, end);
        dispatch("search", { query });
    }

    const medianInterval = Math.round(quantile(allIntervals, 0.5) ?? 0);
    const medianIntervalString = timeSpan(medianInterval * 86400, false);
    const tableData = [
        {
            label: fsrs ? tr.statisticsMedianStability() : tr.statisticsMedianInterval(),
            value: medianIntervalString,
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
