// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/* eslint
@typescript-eslint/no-non-null-assertion: "off",
@typescript-eslint/no-explicit-any: "off",
@typescript-eslint/ban-ts-ignore: "off" */

import pb from "anki/backend_proto";
import type { Selection } from "d3";
import type { PreferencePayload } from "./preferences";
import { postRequest } from "anki/postrequest";

export async function getGraphData(
    search: string,
    days: number
): Promise<pb.BackendProto.GraphsOut> {
    return pb.BackendProto.GraphsOut.decode(
        await postRequest("/_anki/graphData", JSON.stringify({ search, days }))
    );
}

export async function getGraphPreferences(): Promise<pb.BackendProto.GraphPreferences> {
    return pb.BackendProto.GraphPreferences.decode(
        await postRequest("/_anki/graphPreferences", JSON.stringify({}))
    );
}

export async function setGraphPreferences(prefs: PreferencePayload): Promise<void> {
    return (async (): Promise<void> => {
        await postRequest(
            "/_anki/setGraphPreferences",
            pb.BackendProto.GraphPreferences.encode(prefs).finish()
        );
    })();
}

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

export interface GraphsContext {
    cards: pb.BackendProto.Card[];
    revlog: pb.BackendProto.RevlogEntry[];
    revlogRange: RevlogRange;
    nightMode: boolean;
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

export function setDataAvailable(
    svg: Selection<SVGElement, any, any, any>,
    available: boolean
): void {
    svg.select(".no-data")
        .attr("pointer-events", available ? "none" : "all")
        .transition()
        .duration(600)
        .attr("opacity", available ? 0 : 1);
}

export function millisecondCutoffForRange(
    range: GraphRange,
    nextDayAtSecs: number
): number {
    let days;
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
    detail: SearchEventMap[EventKey]
) => void;
