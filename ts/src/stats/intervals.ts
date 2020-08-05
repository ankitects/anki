// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/* eslint
@typescript-eslint/no-non-null-assertion: "off",
@typescript-eslint/no-explicit-any: "off",
 */

import pb from "../backend/proto";
import { extent, histogram, quantile, sum, mean } from "d3-array";
import { scaleLinear, scaleSequential } from "d3-scale";
import { CardQueue } from "../cards";
import { HistogramData } from "./histogram-graph";
import { interpolateBlues } from "d3-scale-chromatic";
import { I18n } from "../i18n";
import { TableDatum } from "./graphs";
import { timeSpan } from "../time";

export interface IntervalGraphData {
    intervals: number[];
}

export enum IntervalRange {
    Month = 0,
    Percentile50 = 1,
    Percentile95 = 2,
    Percentile999 = 3,
    All = 4,
}

export function gatherIntervalData(data: pb.BackendProto.GraphsOut): IntervalGraphData {
    const intervals = (data.cards as pb.BackendProto.Card[])
        .filter((c) => c.queue == CardQueue.Review)
        .map((c) => c.ivl);
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

export function prepareIntervalData(
    data: IntervalGraphData,
    range: IntervalRange,
    i18n: I18n
): [HistogramData | null, TableDatum[]] {
    // get min/max
    const allIntervals = data.intervals;
    if (!allIntervals.length) {
        return [null, []];
    }

    const total = allIntervals.length;
    const [_xMinOrig, origXMax] = extent(allIntervals);
    let xMax = origXMax;

    // cap max to selected range
    switch (range) {
        case IntervalRange.Month:
            xMax = Math.min(xMax!, 31);
            break;
        case IntervalRange.Percentile50:
            xMax = quantile(allIntervals, 0.5);
            break;
        case IntervalRange.Percentile95:
            xMax = quantile(allIntervals, 0.95);
            break;
        case IntervalRange.Percentile999:
            xMax = quantile(allIntervals, 0.999);
            break;
        case IntervalRange.All:
            break;
    }
    const xMin = 0;
    xMax = xMax! + 1;

    // cap bars to available range
    const desiredBars = Math.min(70, xMax! - xMin!);

    const scale = scaleLinear().domain([xMin!, xMax!]).nice();
    const bins = histogram()
        .domain(scale.domain() as any)
        .thresholds(scale.ticks(desiredBars))(allIntervals);

    // empty graph?
    const totalInPeriod = sum(bins, (bin) => bin.length);
    if (!totalInPeriod) {
        return [null, []];
    }

    // start slightly darker
    const shiftedMin = xMin! - Math.round((xMax - xMin!) / 10);
    const colourScale = scaleSequential(interpolateBlues).domain([shiftedMin, xMax]);

    function hoverText(
        data: HistogramData,
        binIdx: number,
        _cumulative: number,
        percent: number
    ): string {
        const bin = data.bins[binIdx];
        // const day = dayLabel(i18n, bin.x0!, bin.x1!);
        const interval = intervalLabel(i18n, bin.x0!, bin.x1!, bin.length);
        const total = i18n.tr(i18n.TR.STATISTICS_RUNNING_TOTAL);
        return `${interval}<br>${total}: \u200e${percent.toFixed(1)}%`;
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
        { scale, bins, total: totalInPeriod, hoverText, colourScale, showArea: true },
        tableData,
    ];
}
