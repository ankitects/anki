// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import throttle from "lodash.throttle";

const tooltipDiv: HTMLDivElement = document.createElement("div");
tooltipDiv.className = "graph-tooltip";
document.body.appendChild(tooltipDiv);

function showTooltipInner(msg: string, x: number, y: number): void {
    tooltipDiv.innerHTML = msg;

    // move tooltip away from edge as user approaches right side
    const shiftLeftAmount = Math.round(
        tooltipDiv.clientWidth * 1.2 * (x / document.body.clientWidth)
    );

    const adjustedX = x + 40 - shiftLeftAmount;
    const adjustedY = y + 40;

    tooltipDiv.style.left = `${adjustedX}px`;
    tooltipDiv.style.top = `${adjustedY}px`;
    tooltipDiv.style.opacity = "1";
}

export const showTooltip = throttle(showTooltipInner, 16);

export function hideTooltip(): void {
    showTooltip.cancel();
    tooltipDiv.style.opacity = "0";
}
