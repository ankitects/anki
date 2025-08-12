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
import { bin, extent, interpolateGreens, scaleLinear, scaleSequential, sum, interpolateViridis, interpolateInferno, interpolateMagma, interpolatePlasma,interpolateCividis } from "d3";

import type { SearchDispatch, TableDatum } from "./graph-helpers";
import { getNumericMapBinValue, GraphRange, numericMap } from "./graph-helpers";
import type { HistogramData } from "./histogram-graph";

export interface GraphData {
    dueCounts: Map<number, number>;
    haveBacklog: boolean;
    dailyLoad: number;
}

export function gatherData(data: GraphsResponse): GraphData {
    const msg = data.futureDue!;
    return {
        dueCounts: numericMap(msg.futureDue),
        haveBacklog: msg.haveBacklog,
        dailyLoad: msg.dailyLoad,
    };
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

function withoutBacklog(data: Map<number, number>): Map<number, number> {
    const map = new Map();
    for (const [day, count] of data.entries()) {
        if (day >= 0) {
            map.set(day, count);
        }
    }
    return map;
}

export function buildHistogram(
    sourceData: GraphData,
    range: GraphRange,
    includeBacklog: boolean,
    dispatch: SearchDispatch,
    browserLinksSupported: boolean,
): FutureDueResponse {
    const output = { histogramData: null, tableData: [] };
    // get min/max
    const data = includeBacklog ? sourceData.dueCounts : withoutBacklog(sourceData.dueCounts);
    if (!data) {
        return output;
    }

    const [xMinOrig, origXMax] = extent<number>(data.keys());
    let xMin = xMinOrig;
    if (!includeBacklog) {
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
    
    let adjustedRange = scaleLinear().range([0.0, 1]);

    const isColorBlindMode = (window as any).colorBlindMode;

    let colourScale;

    if(isColorBlindMode) { 
        colourScale = scaleSequential((n) => interpolateViridis(adjustedRange(n)!)).domain([xMin!, xMax!]);
        adjustedRange = scaleLinear().range([0.0, 1]);
    } else {
        colourScale = scaleSequential((n) => interpolateGreens(adjustedRange(n)!)).domain([xMin!, xMax!]);
        adjustedRange = scaleLinear().range([0.7, 0.3]);
    }


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
        {
            label: tr.statisticsDailyLoad(),
            value: tr.statisticsReviewsPerDay({
                count: sourceData.dailyLoad,
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
