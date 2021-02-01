// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/* eslint
@typescript-eslint/no-explicit-any: "off",
*/

import type { SvelteComponent } from "svelte/internal";

import { setupI18n } from "anki/i18n";
import { checkNightMode } from "anki/nightmode";
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
    { search = "deck:current", days = 365, controller = null as any } = {}
): void {
    const nightMode = checkNightMode();

    setupI18n().then((i18n) => {
        new GraphsPage({
            target,
            props: {
                i18n,
                graphs,
                nightMode,
                search,
                days,
                controller,
            },
        });
    });
}
