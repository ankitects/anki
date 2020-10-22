// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/* eslint
@typescript-eslint/no-non-null-assertion: "off",
@typescript-eslint/no-explicit-any: "off",
 */

import pb from "../backend/proto";
import { extent, histogram, rollup, sum, Bin } from "d3-array";
import { scaleLinear, scaleSequential } from "d3-scale";
import { CardQueue } from "../cards";
import { HistogramData } from "./histogram-graph";
import { interpolateGreens } from "d3-scale-chromatic";
import { dayLabel } from "../time";
import { I18n } from "../i18n";
import { GraphRange, TableDatum } from "./graphs";

export interface GraphData {
    dueCounts: Map<number, number>;
    haveBacklog: boolean;
}

export function gatherData(data: pb.BackendProto.GraphsOut): GraphData {
    const isLearning = (queue: number): boolean =>
        [CardQueue.Learn, CardQueue.PreviewRepeat].includes(queue);
    let haveBacklog = false;
    const due = (data.cards as pb.BackendProto.Card[])
        .filter(
            (c) =>
                // reviews
                [CardQueue.Review, CardQueue.DayLearn].includes(c.queue) ||
                // or learning cards due today
                (isLearning(c.queue) && c.due < data.nextDayAtSecs)
        )
        .map((c) => {
            if (isLearning(c.queue)) {
                return 0;
            } else {
                // - testing just odue fails on day 1
                // - testing just odid fails on lapsed cards that
                //   have due calculated at regraduation time
                const due = c.originalDeckId && c.originalDue ? c.originalDue : c.due;
                const dueDay = due - data.daysElapsed;
                if (dueDay < 0) {
                    haveBacklog = true;
                }
                return dueDay;
            }
        });

    const dueCounts = rollup(
        due,
        (v) => v.length,
        (d) => d
    );
    return { dueCounts, haveBacklog };
}

function binValue(d: Bin<Map<number, number>, number>): number {
    return sum(d, (d) => d[1]);
}

export interface FutureDueOut {
    histogramData: HistogramData | null;
    tableData: TableDatum[];
}

export function buildHistogram(
    sourceData: GraphData,
    range: GraphRange,
    backlog: boolean,
    i18n: I18n
): FutureDueOut {
    const output = { histogramData: null, tableData: [] };
    // get min/max
    const data = sourceData.dueCounts;
    if (!data) {
        return output;
    }

    const [xMinOrig, origXMax] = extent<number>(data.keys());
    let xMin = xMinOrig;
    if (!backlog) {
        xMin = 0;
    }
    let xMax = origXMax;

    // cap max to selected range
    switch (range) {
        case GraphRange.Month:
            xMax = 31;
            break;
        case GraphRange.ThreeMonths:
            xMax = 90;
            break;
        case GraphRange.Year:
            xMax = 365;
            break;
        case GraphRange.AllTime:
            break;
    }
    // cap bars to available range
    const desiredBars = Math.min(70, xMax! - xMin!);

    const x = scaleLinear().domain([xMin!, xMax!]);
    const bins = histogram()
        .value((m) => {
            return m[0];
        })
        .domain(x.domain() as any)
        .thresholds(x.ticks(desiredBars))(data.entries() as any);

    // empty graph?
    if (!sum(bins, (bin) => bin.length)) {
        return output;
    }

    const adjustedRange = scaleLinear().range([0.7, 0.3]);
    const colourScale = scaleSequential((n) =>
        interpolateGreens(adjustedRange(n)!)
    ).domain([xMin!, xMax!]);

    const total = sum(bins as any, binValue);

    function hoverText(
        data: HistogramData,
        binIdx: number,
        cumulative: number,
        _percent: number
    ): string {
        const bin = data.bins[binIdx];
        const days = dayLabel(i18n, bin.x0!, bin.x1!);
        const cards = i18n.tr(i18n.TR.STATISTICS_CARDS_DUE, {
            cards: binValue(data.bins[binIdx] as any),
        });
        const totalLabel = i18n.tr(i18n.TR.STATISTICS_RUNNING_TOTAL);

        return `${days}:<br>${cards}<br>${totalLabel}: ${cumulative}`;
    }

    const periodDays = xMax! - xMin!;
    const tableData = [
        {
            label: i18n.tr(i18n.TR.STATISTICS_TOTAL),
            value: i18n.tr(i18n.TR.STATISTICS_REVIEWS, { reviews: total }),
        },
        {
            label: i18n.tr(i18n.TR.STATISTICS_AVERAGE),
            value: i18n.tr(i18n.TR.STATISTICS_REVIEWS_PER_DAY, {
                count: Math.round(total / periodDays),
            }),
        },
        {
            label: i18n.tr(i18n.TR.STATISTICS_DUE_TOMORROW),
            value: i18n.tr(i18n.TR.STATISTICS_REVIEWS, {
                reviews: sourceData.dueCounts.get(1) ?? 0,
            }),
        },
    ];

    return {
        histogramData: {
            scale: x,
            bins,
            total,
            hoverText,
            showArea: true,
            colourScale,
            binValue,
        },
        tableData,
    };
}
