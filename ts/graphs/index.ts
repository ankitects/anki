// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { SvelteComponentDev } from "svelte/internal";
import { setupI18n, ModuleName } from "../lib/i18n";
import { checkNightMode } from "../lib/nightmode";

import GraphsPage from "./GraphsPage.svelte";
import "./graphs-base.css";

const i18n = setupI18n({ modules: [ModuleName.STATISTICS, ModuleName.SCHEDULING] });

export async function setupGraphs(
    graphs: typeof SvelteComponentDev[],
    {
        search = "deck:current",
        days = 365,
        controller = null as typeof SvelteComponentDev | null,
    } = {},
): Promise<GraphsPage> {
    checkNightMode();
    await i18n;

    return new GraphsPage({
        target: document.body,
        props: {
            initialSearch: search,
            initialDays: days,
            graphs,
            controller,
        },
    });
}

import TodayStats from "./TodayStats.svelte";
import FutureDue from "./FutureDue.svelte";
import CalendarGraph from "./CalendarGraph.svelte";
import ReviewsGraph from "./ReviewsGraph.svelte";
import CardCounts from "./CardCounts.svelte";
import IntervalsGraph from "./IntervalsGraph.svelte";
import EaseGraph from "./EaseGraph.svelte";
import HourGraph from "./HourGraph.svelte";
import ButtonsGraph from "./ButtonsGraph.svelte";
import AddedGraph from "./AddedGraph.svelte";
import { RevlogRange } from "./graph-helpers";
import RangeBox from "./RangeBox.svelte";

setupGraphs(
    [
        TodayStats,
        FutureDue,
        CalendarGraph,
        ReviewsGraph,
        CardCounts,
        IntervalsGraph,
        EaseGraph,
        HourGraph,
        ButtonsGraph,
        AddedGraph,
    ],
    {
        controller: RangeBox,
    },
);

export const graphComponents = {
    TodayStats,
    FutureDue,
    CalendarGraph,
    ReviewsGraph,
    CardCounts,
    IntervalsGraph,
    EaseGraph,
    HourGraph,
    ButtonsGraph,
    AddedGraph,
    RangeBox,
    RevlogRange,
};
