import { css } from "@emotion/css";

export const graph = css`
    margin-left: auto;
    margin-right: auto;
    max-width: 60em;
    page-break-inside: avoid;

    h1 {
        text-align: center;
        margin-bottom: 0.25em;
        margin-top: 1.5em;
    }
`;

export const graphArea = css`
    pointer-events: none;
    fill: var(--area-fill);
    fill-opacity: var(--area-fill-opacity);
    stroke: var(--area-stroke);
    stroke-opacity: var(--area-stroke-opacity);
`;
