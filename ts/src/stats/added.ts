// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/* eslint
@typescript-eslint/no-non-null-assertion: "off",
@typescript-eslint/no-explicit-any: "off",
 */

import pb from "../backend/proto";
import { extent, histogram } from "d3-array";
import { scaleLinear, scaleSequential } from "d3-scale";
import { HistogramData } from "./histogram-graph";
import { interpolateBlues } from "d3-scale-chromatic";

export enum AddedRange {
    Month = 0,
    Quarter = 1,
    Year = 2,
    AllTime = 3,
}

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

function hoverText(
    data: HistogramData,
    binIdx: number,
    cumulative: number,
    _percent: number
): string {
    const bin = data.bins[binIdx];
    return (
        `${bin.length} at ${bin.x1! - 1} days.<br>` +
        ` ${cumulative} cards at or below this point.`
    );
}

export function prepareData(data: GraphData, range: AddedRange): HistogramData | null {
    // get min/max
    const total = data.daysAdded.length;
    if (!total) {
        return null;
    }

    const [xMinOrig, _xMax] = extent(data.daysAdded);
    let xMin = xMinOrig;

    // cap max to selected range
    switch (range) {
        case AddedRange.Month:
            xMin = -31;
            break;
        case AddedRange.Quarter:
            xMin = -90;
            break;
        case AddedRange.Year:
            xMin = -365;
            break;
        case AddedRange.AllTime:
            break;
    }
    const xMax = 1;
    const desiredBars = Math.min(70, Math.abs(xMin!));

    const scale = scaleLinear().domain([xMin!, xMax]);
    const bins = histogram()
        .domain(scale.domain() as any)
        .thresholds(scale.ticks(desiredBars))(data.daysAdded);

    const colourScale = scaleSequential(interpolateBlues).domain([xMin!, xMax]);

    return { scale, bins, total, hoverText, colourScale, showArea: true };
}
