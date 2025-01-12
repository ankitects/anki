// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/* eslint
@typescript-eslint/no-explicit-any: "off",
 */

import "./graphs-base.scss";

import { ModuleName, setupI18n } from "@tslib/i18n";
import { checkNightMode } from "@tslib/nightmode";
import type { SvelteComponent } from "svelte";

import GraphsPage from "./GraphsPage.svelte";

const i18n = setupI18n({ modules: [ModuleName.STATISTICS, ModuleName.SCHEDULING] });

export async function setupGraphs(
    graphs: typeof SvelteComponent<any>[],
    {
        search = "deck:current",
        days = 365,
        controller = null satisfies typeof SvelteComponent<any> | null,
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

import AddedGraph from "./AddedGraph.svelte";
import ButtonsGraph from "./ButtonsGraph.svelte";
import CalendarGraph from "./CalendarGraph.svelte";
import CardCounts from "./CardCounts.svelte";
import DifficultyGraph from "./DifficultyGraph.svelte";
import EaseGraph from "./EaseGraph.svelte";
import FutureDue from "./FutureDue.svelte";
import { RevlogRange } from "./graph-helpers";
import HourGraph from "./HourGraph.svelte";
import IntervalsGraph from "./IntervalsGraph.svelte";
import RangeBox from "./RangeBox.svelte";
import RetrievabilityGraph from "./RetrievabilityGraph.svelte";
import ReviewsGraph from "./ReviewsGraph.svelte";
import StabilityGraph from "./StabilityGraph.svelte";
import TodayStats from "./TodayStats.svelte";

export const graphComponents = {
    TodayStats,
    FutureDue,
    CalendarGraph,
    ReviewsGraph,
    CardCounts,
    IntervalsGraph,
    StabilityGraph,
    EaseGraph,
    DifficultyGraph,
    RetrievabilityGraph,
    HourGraph,
    ButtonsGraph,
    AddedGraph,
    RangeBox,
    RevlogRange,
};
