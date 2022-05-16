// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/* eslint
@typescript-eslint/no-explicit-any: "off",
 */

import type { Bin } from "d3";
import {
    extent,
    histogram,
    interpolateGreens,
    rollup,
    scaleLinear,
    scaleSequential,
    sum,
} from "d3";

import { CardType } from "../lib/cards";
import * as tr from "../lib/ftl";
import { localizedNumber } from "../lib/i18n";
import type { Cards, Stats } from "../lib/proto";
import { dayLabel } from "../lib/time";
import type { SearchDispatch, TableDatum } from "./graph-helpers";
import { GraphRange } from "./graph-helpers";
import type { HistogramData } from "./histogram-graph";

export interface GraphData {
    dueCounts: Map<number, number>;
    haveBacklog: boolean;
}

export function gatherData(data: Stats.GraphsResponse): GraphData {
    const isIntradayLearning = (card: Cards.Card, due: number): boolean => {
        return (
            [CardType.Learn, CardType.Relearn].includes(card.ctype) &&
            due > 1_000_000_000
        );
    };
    let haveBacklog = false;
    const due = (data.cards as Cards.Card[])
        .filter((c: Cards.Card) => c.queue > 0)
        .map((c: Cards.Card) => {
            // - testing just odue fails on day 1
            // - testing just odid fails on lapsed cards that
            //   have due calculated at regraduation time
            const due = c.originalDeckId && c.originalDue ? c.originalDue : c.due;

            let dueDay: number;
            if (isIntradayLearning(c, due)) {
                const offset = due - data.nextDayAtSecs;
                dueDay = Math.floor(offset / 86_400) + 1;
            } else {
                dueDay = due - data.daysElapsed;
            }

            haveBacklog = haveBacklog || dueDay < 0;

            return dueDay;
        });

    const dueCounts = rollup(
        due,
        (v) => v.length,
        (d) => d,
    );
    return { dueCounts, haveBacklog };
}

function binValue(d: Bin<Map<number, number>, number>): number {
    return sum(d, (d) => d[1]);
}

export interface FutureDueResponse {
    histogramData: HistogramData | null;
    tableData: TableDatum[];
}

function makeQuery(start: number, end: number): string {
    if (start === end) {
        return `"prop:due=${start}"`;
    } else {
        const fromQuery = `"prop:due>=${start}"`;
        const tillQuery = `"prop:due<=${end}"`;
        return `${fromQuery} AND ${tillQuery}`;
    }
}

export function buildHistogram(
    sourceData: GraphData,
    range: GraphRange,
    backlog: boolean,
    dispatch: SearchDispatch,
    browserLinksSupported: boolean,
): FutureDueResponse {
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

    const xTickFormat = (n: number): string => localizedNumber(n);
    const adjustedRange = scaleLinear().range([0.7, 0.3]);
    const colourScale = scaleSequential((n) =>
        interpolateGreens(adjustedRange(n)!),
    ).domain([xMin!, xMax!]);

    const total = sum(bins as any, binValue);

    function hoverText(
        bin: Bin<number, number>,
        cumulative: number,
        _percent: number,
    ): string {
        const days = dayLabel(bin.x0!, bin.x1!);
        const cards = tr.statisticsCardsDue({
            cards: binValue(bin as any),
        });
        const totalLabel = tr.statisticsRunningTotal();

        return `${days}:<br>${cards}<br>${totalLabel}: ${localizedNumber(cumulative)}`;
    }

    function onClick(bin: Bin<number, number>): void {
        const start = bin.x0!;
        const end = bin.x1! - 1;
        const query = makeQuery(start, end);
        dispatch("search", { query });
    }

    const periodDays = xMax! - xMin!;
    const tableData = [
        {
            label: tr.statisticsTotal(),
            value: tr.statisticsReviews({ reviews: total }),
        },
        {
            label: tr.statisticsAverage(),
            value: tr.statisticsReviewsPerDay({
                count: Math.round(total / periodDays),
            }),
        },
        {
            label: tr.statisticsDueTomorrow(),
            value: tr.statisticsReviews({
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
            onClick: browserLinksSupported ? onClick : null,
            showArea: true,
            colourScale,
            binValue,
            xTickFormat,
        },
        tableData,
    };
}
