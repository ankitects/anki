// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/* eslint
@typescript-eslint/no-non-null-assertion: "off",
@typescript-eslint/no-explicit-any: "off",
 */

import pb from "../backend/proto";
import { extent, histogram, sum, mean } from "d3-array";
import { scaleLinear, scaleSequential } from "d3-scale";
import { HistogramData } from "./histogram-graph";
import { interpolateBlues } from "d3-scale-chromatic";
import { I18n } from "../i18n";
import { dayLabel } from "../time";
import { GraphRange, TableDatum } from "./graphs";

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

export function buildHistogram(
    data: GraphData,
    range: GraphRange,
    i18n: I18n
): [HistogramData | null, TableDatum[]] {
    // get min/max
    const total = data.daysAdded.length;
    if (!total) {
        return [null, []];
    }

    const [xMinOrig, _xMax] = extent(data.daysAdded);
    let xMin = xMinOrig;

    // cap max to selected range
    switch (range) {
        case GraphRange.Month:
            xMin = -31;
            break;
        case GraphRange.ThreeMonths:
            xMin = -90;
            break;
        case GraphRange.Year:
            xMin = -365;
            break;
        case GraphRange.AllTime:
            break;
    }
    const xMax = 1;
    const desiredBars = Math.min(70, Math.abs(xMin!));

    const scale = scaleLinear().domain([xMin!, xMax]);
    const bins = histogram()
        .domain(scale.domain() as any)
        .thresholds(scale.ticks(desiredBars))(data.daysAdded);

    // empty graph?
    if (!sum(bins, (bin) => bin.length)) {
        return [null, []];
    }

    const adjustedRange = scaleLinear().range([0.7, 0.3]);
    const colourScale = scaleSequential((n) =>
        interpolateBlues(adjustedRange(n))
    ).domain([xMax!, xMin!]);

    const totalInPeriod = sum(bins, (bin) => bin.length);
    const cardsPerDay = Math.round(mean(bins, (bin) => bin.length) ?? 0);
    const tableData = [
        {
            label: i18n.tr(i18n.TR.STATISTICS_TOTAL),
            value: i18n.tr(i18n.TR.STATISTICS_CARDS, { cards: totalInPeriod }),
        },
        {
            label: i18n.tr(i18n.TR.STATISTICS_AVERAGE),
            value: i18n.tr(i18n.TR.STATISTICS_CARDS_PER_DAY, { count: cardsPerDay }),
        },
    ];

    function hoverText(
        data: HistogramData,
        binIdx: number,
        cumulative: number,
        _percent: number
    ): string {
        const bin = data.bins[binIdx];
        const day = dayLabel(i18n, bin.x0!, bin.x1!);
        const cards = i18n.tr(i18n.TR.STATISTICS_CARDS, { cards: bin.length });
        const total = i18n.tr(i18n.TR.STATISTICS_RUNNING_TOTAL);
        const totalCards = i18n.tr(i18n.TR.STATISTICS_CARDS, { cards: cumulative });
        return `${day}:<br>${cards}<br>${total}: ${totalCards}`;
    }

    return [
        { scale, bins, total: totalInPeriod, hoverText, colourScale, showArea: true },
        tableData,
    ];
}
