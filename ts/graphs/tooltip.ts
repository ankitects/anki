// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { throttle, DebouncedFunc } from "lodash-es";
import Tooltip from "./Tooltip.svelte";

let tooltip: Tooltip | null = null;

function getOrCreateTooltip(): Tooltip {
    if (tooltip) {
        return tooltip;
    }

    const target = document.createElement("div");
    tooltip = new Tooltip({ target });
    document.body.appendChild(target);

    return tooltip;
}

function showTooltipInner(msg: string, x: number, y: number): void {
    const tooltip = getOrCreateTooltip();

    tooltip.$set({ html: msg, x, y, show: true });
}

export const showTooltip: DebouncedFunc<(msg: string, x: number, y: number) => void> =
    throttle(showTooltipInner, 16);

export function hideTooltip(): void {
    const tooltip = getOrCreateTooltip();
    showTooltip.cancel();
    tooltip.$set({ show: false });
}
