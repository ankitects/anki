import { css, cx } from "@emotion/css";

export const graphArea = cx(
    "area",
    css`
        pointer-events: none;
        fill: var(--area-fill);
        fill-opacity: var(--area-fill-opacity);
        stroke: var(--area-stroke);
        stroke-opacity: var(--area-stroke-opacity);
    `
);

export const graphHoverzone = cx(
    "hoverzone",
    css`
        rect {
            fill: none;
            pointer-events: all;

            &:hover {
                fill: grey;
                opacity: 0.05;
            }
        }
    `
);
