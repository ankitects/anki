// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import throttle from "lodash.throttle";
import { css } from "@emotion/css";

let tooltip: HTMLDivElement | null = null;

function getOrCreateTooltipDiv(): HTMLDivElement {
    if (tooltip) {
        return tooltip;
    }

    tooltip = document.createElement("div");
    tooltip.className = css`
        position: absolute;
        padding: 15px;
        border-radius: 5px;
        font-size: 15px;
        opacity: 0;
        pointer-events: none;
        transition: opacity 0.3s;
        color: var(--text-fg);
        background: var(--tooltip-bg);

        table {
            border-spacing: 1em 0;
        }
    `;

    document.body.appendChild(tooltip);

    return tooltip;
}

function showTooltipInner(msg: string, x: number, y: number): void {
    const tooltipDiv = getOrCreateTooltipDiv();

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
    const tooltipDiv = getOrCreateTooltipDiv();

    showTooltip.cancel();
    tooltipDiv.style.opacity = "0";
}
