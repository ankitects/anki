// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/* eslint
@typescript-eslint/no-non-null-assertion: "off",
@typescript-eslint/no-explicit-any: "off",
 */

import pb from "../backend/proto";
import { extent, histogram } from "d3-array";
import { scaleLinear, scaleSequential } from "d3-scale";
import { CardQueue } from "../cards";
import { HistogramData } from "./histogram-graph";
import { interpolateRdYlGn } from "d3-scale-chromatic";

export interface GraphData {
    eases: number[];
}

export function gatherData(data: pb.BackendProto.GraphsOut): GraphData {
    const eases = (data.cards as pb.BackendProto.Card[])
        .filter((c) => c.queue == CardQueue.Review)
        .map((c) => c.factor / 10);
    return { eases };
}

function hoverText(data: HistogramData, binIdx: number, _percent: number): string {
    const bin = data.bins[binIdx];
    const minPct = Math.floor(bin.x0!);
    const maxPct = Math.floor(bin.x1!);
    const ease = maxPct - minPct <= 10 ? `${bin.x0}%` : `${bin.x0}%~${bin.x1}%`;

    return `${bin.length} cards with ${ease} ease.`;
}

export function prepareData(data: GraphData): HistogramData | null {
    // get min/max
    const allEases = data.eases;
    if (!allEases.length) {
        return null;
    }
    const total = allEases.length;
    const [_xMin, origXMax] = extent(allEases);
    let xMax = origXMax;
    const xMin = 130;

    xMax = xMax! + 1;
    const desiredBars = 20;

    const scale = scaleLinear().domain([130, xMax!]).nice();
    const bins = histogram()
        .domain(scale.domain() as any)
        .thresholds(scale.ticks(desiredBars))(allEases);

    const colourScale = scaleSequential(interpolateRdYlGn).domain([xMin, 300]);

    return { scale, bins, total, hoverText, colourScale, showArea: false };
}
