// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/* eslint
@typescript-eslint/no-non-null-assertion: "off",
@typescript-eslint/no-explicit-any: "off",
 */

import pb from "../backend/proto";
import { extent, histogram, rollup, max, sum, Bin } from "d3-array";
import { scaleLinear, scaleSequential } from "d3-scale";
import { CardQueue } from "../cards";
import { HistogramData } from "./histogram-graph";
import { interpolateGreens } from "d3-scale-chromatic";

export interface GraphData {
    dueCounts: Map<number, number>;
}

export enum FutureDueRange {
    Month = 0,
    Quarter = 1,
    Year = 2,
    AllTime = 3,
}

export function gatherData(data: pb.BackendProto.GraphsOut): GraphData {
    const due = (data.cards as pb.BackendProto.Card[])
        .filter((c) => c.queue == CardQueue.Review) //  && c.due >= data.daysElapsed)
        .map((c) => c.due - data.daysElapsed);
    const dueCounts = rollup(
        due,
        (v) => v.length,
        (d) => d
    );
    return { dueCounts };
}

function binValue(d: Bin<Map<number, number>, number>): number {
    return sum(d, (d) => d[1]);
}

function hoverText(
    data: HistogramData,
    binIdx: number,
    cumulative: number,
    _percent: number
): string {
    const bin = data.bins[binIdx];
    const interval =
        bin.x1! - bin.x0! === 1 ? `${bin.x0} days` : `${bin.x0}~${bin.x1} days`;
    return (
        `${binValue(data.bins[binIdx] as any)} cards due in ${interval}. ` +
        `<br>${cumulative} cards at or before this point.`
    );
}

export function buildHistogram(
    sourceData: GraphData,
    range: FutureDueRange
): HistogramData | null {
    // get min/max
    const data = sourceData.dueCounts;
    if (!data) {
        return null;
    }

    const [xMinOrig, origXMax] = extent<number>(data.keys());
    const xMin = 0;
    let xMax = origXMax;

    // cap max to selected range
    switch (range) {
        case FutureDueRange.Month:
            xMax = 31;
            break;
        case FutureDueRange.Quarter:
            xMax = 90;
            break;
        case FutureDueRange.Year:
            xMax = 365;
            break;
        case FutureDueRange.AllTime:
            break;
    }
    xMax = xMax! + 1;

    // cap bars to available range
    const desiredBars = Math.min(70, xMax! - xMin!);

    const x = scaleLinear().domain([xMin!, xMax!]).nice();
    const bins = histogram()
        .value((m) => {
            return m[0];
        })
        .domain(x.domain() as any)
        .thresholds(x.ticks(desiredBars))(data.entries() as any);

    // start slightly darker
    const shiftedMin = xMin! - Math.round((xMax - xMin!) / 10);
    const colourScale = scaleSequential(interpolateGreens).domain([shiftedMin, xMax]);

    const total = sum(bins as any, binValue);

    return {
        scale: x,
        bins,
        total,
        hoverText,
        showArea: true,
        colourScale,
        binValue,
    };
}
