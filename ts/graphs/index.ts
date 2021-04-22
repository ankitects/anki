// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { SvelteComponent } from "svelte/internal";

import { setupI18n, ModuleName } from "lib/i18n";
import { checkNightMode } from "lib/nightmode";

import GraphsPage from "./GraphsPage.svelte";

export { default as RangeBox } from "./RangeBox.svelte";

export { default as IntervalsGraph } from "./IntervalsGraph.svelte";
export { default as EaseGraph } from "./EaseGraph.svelte";
export { default as AddedGraph } from "./AddedGraph.svelte";
export { default as TodayStats } from "./TodayStats.svelte";
export { default as ButtonsGraph } from "./ButtonsGraph.svelte";
export { default as CardCounts } from "./CardCounts.svelte";
export { default as HourGraph } from "./HourGraph.svelte";
export { default as FutureDue } from "./FutureDue.svelte";
export { default as ReviewsGraph } from "./ReviewsGraph.svelte";
export { default as CalendarGraph } from "./CalendarGraph.svelte";
export { RevlogRange } from "./graph-helpers";

export function graphs(
    target: HTMLDivElement,
    graphs: SvelteComponent[],
    {
        search = "deck:current",
        days = 365,
        controller = null as SvelteComponent | null,
    } = {}
): void {
    const nightMode = checkNightMode();

    setupI18n({ modules: [ModuleName.STATISTICS, ModuleName.SCHEDULING] }).then(() => {
        new GraphsPage({
            target,
            props: {
                graphs,
                nightMode,
                initialSearch: search,
                initialDays: days,
                controller,
            },
        });
    });
}
