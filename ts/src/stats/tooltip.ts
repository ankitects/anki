// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import throttle from "lodash.throttle";

let tooltipDiv: HTMLDivElement | null = null;

function showTooltipInner(msg: string, x: number, y: number): void {
    if (!tooltipDiv) {
        tooltipDiv = document.createElement("div");
        tooltipDiv.className = "graph-tooltip";
        document.body.appendChild(tooltipDiv);
    }
    tooltipDiv.innerHTML = msg;
    tooltipDiv.style.left = `${x - 50}px`;
    tooltipDiv.style.top = `${y - 50}px`;

    tooltipDiv.style.opacity = "1";
}

export const showTooltip = throttle(showTooltipInner, 16);

export function hideTooltip(): void {
    showTooltip.cancel();
    if (tooltipDiv) {
        tooltipDiv.style.opacity = "0";
    }
}
