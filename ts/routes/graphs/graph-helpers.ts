// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/* eslint
@typescript-eslint/no-explicit-any: "off",
@typescript-eslint/ban-ts-comment: "off" */

import type { GraphPreferences } from "@generated/anki/stats_pb";
import type { Bin, ScaleLinear, Selection } from "d3";
import { sum } from "d3";

import type { PreferenceStore } from "$lib/sveltelib/preferences";

// amount of data to fetch from backend
export enum RevlogRange {
    Year = 1,
    All = 2,
}

export function daysToRevlogRange(days: number): RevlogRange {
    return days > 365 || days === 0 ? RevlogRange.All : RevlogRange.Year;
}

// period a graph should cover
export enum GraphRange {
    Month = 0,
    ThreeMonths = 1,
    Year = 2,
    AllTime = 3,
}

export interface GraphBounds {
    width: number;
    height: number;
    marginLeft: number;
    marginRight: number;
    marginTop: number;
    marginBottom: number;
}

export function defaultGraphBounds(): GraphBounds {
    return {
        width: 600,
        height: 250,
        marginLeft: 70,
        marginRight: 70,
        marginTop: 20,
        marginBottom: 25,
    };
}

export type GraphPrefs = PreferenceStore<GraphPreferences>;

export function setDataAvailable(
    svg: Selection<SVGElement, any, any, any>,
    available: boolean,
): void {
    svg.select(".no-data")
        .attr("pointer-events", available ? "none" : "all")
        .transition()
        .duration(600)
        .attr("opacity", available ? 0 : 1);
}

export function millisecondCutoffForRange(
    range: GraphRange,
    nextDayAtSecs: number,
): number {
    let days: number;
    switch (range) {
        case GraphRange.Month:
            days = 31;
            break;
        case GraphRange.ThreeMonths:
            days = 90;
            break;
        case GraphRange.Year:
            days = 365;
            break;
        case GraphRange.AllTime:
        default:
            return 0;
    }

    return (nextDayAtSecs - 86400 * days) * 1000;
}

export interface TableDatum {
    label: string;
    value: string;
}

export type SearchEventMap = { search: { query: string } };
export type SearchDispatch = <EventKey extends Extract<keyof SearchEventMap, string>>(
    type: EventKey,
    detail: SearchEventMap[EventKey],
) => void;

/** Convert a protobuf map that protobufjs represents as an object with string
keys into a Map with numeric keys. */
export function numericMap<T>(obj: { [k: string]: T }): Map<number, T> {
    return new Map(Object.entries(obj).map(([k, v]) => [Number(k), v]));
}

export function getNumericMapBinValue(d: Bin<Map<number, number>, number>): number {
    return sum(d, (d) => d[1]);
}

/** Thresholds for d3's bin() over the scale's domain, taken from its ticks. d3
puts the days between a domain end and the nearest tick in a bin of their own,
which can be a day wide or less and too thin to draw, so a bin at either end
that is narrower than the rest is merged into its neighbour. */
export function binThresholds(scale: ScaleLinear<number, number>, count: number): number[] {
    const ticks = scale.ticks(count);
    const [min, max] = scale.domain();
    const thresholds = ticks.filter((t) => t > min && t < max);
    if (ticks.length >= 2) {
        const step = ticks[1] - ticks[0];
        if (thresholds.length && thresholds[0] - min < step) {
            thresholds.shift();
        }
        if (thresholds.length && max - thresholds[thresholds.length - 1] < step) {
            thresholds.pop();
        }
    }
    return thresholds;
}
