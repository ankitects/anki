import { css, cx, injectGlobal } from "@emotion/css";

injectGlobal`
    :root {
        --area-fill: #000000;
        --area-fill-opacity: 0.03;
        --area-stroke: #000000;
        --area-stroke-opacity: 0.08;
    }

    :root[class*="night-mode"] {
        --area-fill: #ffffff;
        --area-fill-opacity: 0.08;
        --area-stroke: #000000;
        --area-stroke-opacity: 0.18;
    }
`;

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

export const tickCustom = css`
    line {
        opacity: 0.1;
    }

    text {
        opacity: 0.5;
        font-size: 10px;

        @media only screen and (max-width: 800px) {
            font-size: 13px;
        }

        @media only screen and (max-width: 600px) {
            font-size: 16px;
        }
    }
    }
`;

export const clickable = css`
    cursor: pointer;
`;
