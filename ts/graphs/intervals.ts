// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/* eslint
@typescript-eslint/no-explicit-any: "off",
 */

import * as tr from "@tslib/ftl";
import { localizedNumber } from "@tslib/i18n";
import type { Stats } from "@tslib/proto";
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

export function gatherIntervalData(data: Stats.GraphsResponse): IntervalGraphData {
    // This could be made more efficient - this graph currently expects a flat list of individual intervals which it
    // uses to calculate a percentile and then converts into a histogram, and the percentile/histogram calculations
    // in JS are relatively slow.
    const map = numericMap(data.intervals!.intervals);
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
): string {
    if (daysEnd - daysStart <= 1) {
        // singular
        return tr.statisticsIntervalsDaySingle({
            day: daysStart,
            cards,
        });
    } else {
        // range
        return tr.statisticsIntervalsDayRange({
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
    dispatch: SearchDispatch,
    browserLinksSupported: boolean,
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
        const interval = intervalLabel(bin.x0!, bin.x1!, bin.length);
        const total = tr.statisticsRunningTotal();
        return `${interval}<br>${total}: \u200e${localizedNumber(percent, 1)}%`;
    }

    function onClick(bin: Bin<number, number>): void {
        const start = bin.x0!;
        const end = bin.x1! - 1;
        const query = makeQuery(start, end);
        dispatch("search", { query });
    }

    const meanInterval = Math.round(mean(allIntervals) ?? 0);
    const meanIntervalString = timeSpan(meanInterval * 86400, false);
    const tableData = [
        {
            label: tr.statisticsAverageInterval(),
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
