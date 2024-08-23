// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/* eslint
@typescript-eslint/no-explicit-any: "off",
 */

import type { GraphsResponse } from "@generated/anki/stats_pb";
import * as tr from "@generated/ftl";
import { localizedNumber } from "@tslib/i18n";
import { dayLabel } from "@tslib/time";
import type { Bin } from "d3";
import { bin, extent, interpolateGreens, scaleLinear, scaleSequential, sum } from "d3";

import type { SearchDispatch, TableDatum } from "./graph-helpers";
import { getNumericMapBinValue, GraphRange, numericMap } from "./graph-helpers";
import type { HistogramData } from "./histogram-graph";

export interface GraphData {
    dueCounts: Map<number, number>;
    haveBacklog: boolean;
}

export function gatherData(data: GraphsResponse): GraphData {
    const msg = data.futureDue!;
    return { dueCounts: numericMap(msg.futureDue), haveBacklog: msg.haveBacklog };
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

function getDueCounts(data, backlog) {
    // if we're showing the backlog we don't need to do any extra processing
    if (backlog) {
        return data;
    }

    // if we're not showing the backlog, add those cards to what is due today
    const backlog_count = data.entries().reduce((backlog_count, [days, count]) => {
        if (days < 0) {
            backlog_count += count;
        }
        return backlog_count;
    }, 0);

    const modified_data = new Map(
        data.entries()
            .filter(([day, _count]) => {
                if (day < 0) {
                    return false;
                }
                return true;
            }),
    );

    modified_data.set(0, modified_data.get(0) + backlog_count);
    return modified_data;
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
    const data = getDueCounts(sourceData.dueCounts, backlog);
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
    const bins = bin()
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
    const colourScale = scaleSequential((n) => interpolateGreens(adjustedRange(n)!)).domain([xMin!, xMax!]);

    const total = sum(bins as any, getNumericMapBinValue);

    function hoverText(
        bin: Bin<number, number>,
        cumulative: number,
        _percent: number,
    ): string {
        const days = dayLabel(bin.x0!, bin.x1 === xMax ? bin.x1! + 1 : bin.x1!);
        const cards = tr.statisticsCardsDue({
            cards: getNumericMapBinValue(bin as any),
        });
        const totalLabel = tr.statisticsRunningTotal();

        return `${days}:<br>${cards}<br>${totalLabel}: ${localizedNumber(cumulative)}`;
    }

    function onClick(bin: Bin<number, number>): void {
        const start = bin.x0!;
        // x1 in last bin is inclusive
        const end = bin.x1 === xMax ? bin.x1! : bin.x1! - 1;
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
            binValue: getNumericMapBinValue,
            xTickFormat,
        },
        tableData,
    };
}
