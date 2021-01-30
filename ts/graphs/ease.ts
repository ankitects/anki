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
    interpolateRdYlGn,
} from "d3";
import type { Bin, ScaleLinear } from "d3";
import { CardType } from "anki/cards";
import type { HistogramData } from "./histogram-graph";
import type { I18n } from "anki/i18n";
import type { TableDatum, SearchDispatch } from "./graph-helpers";

export interface GraphData {
    eases: number[];
}

export function gatherData(data: pb.BackendProto.GraphsOut): GraphData {
    const eases = (data.cards as pb.BackendProto.Card[])
        .filter((c) => [CardType.Review, CardType.Relearn].includes(c.ctype))
        .map((c) => c.easeFactor / 10);
    return { eases };
}

function makeQuery(start: number, end: number): string {
    if (start === end) {
        return `"prop:ease=${start / 100}"`;
    }

    const fromQuery = `"prop:ease>=${start / 100}"`;
    const tillQuery = `"prop:ease<${(end + 1) / 100}"`;

    return `${fromQuery} AND ${tillQuery}`;
}

function getAdjustedScaleAndTicks(
    min: number,
    max: number,
    desiredBars: number
): [ScaleLinear<number, number, never>, number[]] {
    const prescale = scaleLinear().domain([min, max]).nice();
    const ticks = prescale.ticks(desiredBars);

    const predomain = prescale.domain() as [number, number];

    const minOffset = min - predomain[0];
    const tickSize = ticks[1] - ticks[0];

    if (minOffset === 0 || (minOffset % tickSize !== 0 && tickSize % minOffset !== 0)) {
        return [prescale, ticks];
    }

    const add = (n: number): number => n + minOffset;
    return [
        scaleLinear().domain(predomain.map(add) as [number, number]),
        ticks.map(add),
    ];
}

export function prepareData(
    data: GraphData,
    i18n: I18n,
    dispatch: SearchDispatch,
    browserLinksSupported: boolean
): [HistogramData | null, TableDatum[]] {
    // get min/max
    const allEases = data.eases;
    if (!allEases.length) {
        return [null, []];
    }
    const total = allEases.length;
    const [, origXMax] = extent(allEases);
    const xMin = 130;
    const xMax = origXMax! + 1;
    const desiredBars = 20;

    const [scale, ticks] = getAdjustedScaleAndTicks(xMin, xMax, desiredBars);

    const bins = histogram()
        .domain(scale.domain() as [number, number])
        .thresholds(ticks)(allEases);

    const colourScale = scaleSequential(interpolateRdYlGn).domain([xMin, 300]);

    function hoverText(bin: Bin<number, number>, _percent: number): string {
        const minPct = Math.floor(bin.x0!);
        const maxPct = Math.floor(bin.x1!);
        const percent = maxPct - minPct <= 10 ? `${bin.x0}%` : `${bin.x0}%-${bin.x1}%`;
        return i18n.tr(i18n.TR.STATISTICS_CARD_EASE_TOOLTIP, {
            cards: bin.length,
            percent,
        });
    }

    function onClick(bin: Bin<number, number>): void {
        const start = bin.x0!;
        const end = bin.x1! - 1;
        const query = makeQuery(start, end);
        dispatch("search", { query });
    }

    const xTickFormat = (num: number): string => `${num.toFixed(0)}%`;
    const tableData = [
        {
            label: i18n.tr(i18n.TR.STATISTICS_AVERAGE_EASE),
            value: xTickFormat(sum(allEases) / total),
        },
    ];

    return [
        {
            scale,
            bins,
            total,
            hoverText,
            onClick: browserLinksSupported ? onClick : null,
            colourScale,
            showArea: false,
            xTickFormat,
        },
        tableData,
    ];
}
