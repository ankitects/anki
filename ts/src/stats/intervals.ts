// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/* eslint
@typescript-eslint/no-non-null-assertion: "off",
@typescript-eslint/no-explicit-any: "off",
 */

import pb from "../backend/proto";
import { extent, histogram, quantile } from "d3-array";
import { scaleLinear } from "d3-scale";
import { CardQueue } from "../cards";
import { HistogramData, histogramGraph } from "./histogram-graph";
import { GraphBounds } from "./graphs";

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
    const intervals = (data.cards2 as pb.BackendProto.Card[])
        .filter((c) => c.queue == CardQueue.Review)
        .map((c) => c.ivl);
    return { intervals };
}

function prepareIntervalData(
    data: IntervalGraphData,
    range: IntervalRange
): HistogramData {
    // get min/max
    const allIntervals = data.intervals;
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

    // cap bars to available range
    const desiredBars = Math.min(70, xMax! - xMin!);

    const scale = scaleLinear().domain([xMin!, xMax!]);
    const bins = histogram()
        .domain(scale.domain() as any)
        .thresholds(scale.ticks(desiredBars))(allIntervals);

    return { scale, bins, total };
}

export function intervalGraph(
    svgElem: SVGElement,
    bounds: GraphBounds,
    data: IntervalGraphData,
    range: IntervalRange
): void {
    const histogram = prepareIntervalData(data, range);
    histogramGraph(svgElem, bounds, histogram);
}
