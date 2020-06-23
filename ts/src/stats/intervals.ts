// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/* eslint
@typescript-eslint/no-non-null-assertion: "off",
@typescript-eslint/no-explicit-any: "off",
 */

import pb from "../backend/proto";
import { extent, histogram, quantile } from "d3-array";
import { scaleLinear, scaleSequential } from "d3-scale";
import { CardQueue } from "../cards";
import { HistogramData } from "./histogram-graph";
import { interpolateBlues } from "d3-scale-chromatic";

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

function hoverText(
    data: HistogramData,
    binIdx: number,
    _cumulative: number,
    percent: number
): string {
    const bin = data.bins[binIdx];
    const interval =
        bin.x1! - bin.x0! === 1
            ? `${bin.x0} day interval`
            : `${bin.x0}~${bin.x1} day interval`;
    return (
        `${bin.length} cards with ${interval}. ` +
        `<br>${percent.toFixed(1)}% cards at or before this point.`
    );
}

export function prepareIntervalData(
    data: IntervalGraphData,
    range: IntervalRange
): HistogramData | null {
    // get min/max
    const allIntervals = data.intervals;
    if (!allIntervals.length) {
        return null;
    }

    const total = allIntervals.length;
    const [xMin, origXMax] = extent(allIntervals);
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
    xMax = xMax! + 1;

    // cap bars to available range
    const desiredBars = Math.min(70, xMax! - xMin!);

    const scale = scaleLinear().domain([xMin!, xMax!]).nice();
    const bins = histogram()
        .domain(scale.domain() as any)
        .thresholds(scale.ticks(desiredBars))(allIntervals);

    // start slightly darker
    const shiftedMin = xMin! - Math.round((xMax - xMin!) / 10);
    const colourScale = scaleSequential(interpolateBlues).domain([shiftedMin, xMax]);

    return { scale, bins, total, hoverText, colourScale, showArea: true };
}
