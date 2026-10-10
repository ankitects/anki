// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
// @vitest-environment jsdom

import { bin, interpolateGreens, scaleLinear, scaleSequential } from "d3";
import { afterEach, expect, test, vi } from "vitest";

import { defaultGraphBounds } from "./graph-helpers";
import { clickableClass } from "./graph-styles";
import type { HistogramData } from "./histogram-graph";
import { histogramGraph } from "./histogram-graph";

afterEach(() => {
    document.body.innerHTML = "";
});

function histogram(values: number[], onClick: HistogramData["onClick"]): HistogramData {
    return {
        scale: scaleLinear().domain([0, 4]),
        bins: bin().domain([0, 4]).thresholds([1, 2, 3])(values),
        total: values.length,
        hoverText: () => "",
        onClick,
        showArea: false,
        colourScale: scaleSequential(interpolateGreens).domain([0, 4]),
    };
}

test("a bin that becomes empty after the data changes is no longer clickable", () => {
    document.body.innerHTML = `<svg>
        <g class="bars"></g><g class="hover-columns"></g><path class="cumulative-overlay"></path>
        <g class="x-ticks"></g><g class="y-ticks"></g><g class="y2-ticks"></g><g class="no-data"></g>
    </svg>`;
    const svg = document.querySelector("svg")!;
    const onClick = vi.fn();
    histogramGraph(svg, defaultGraphBounds(), histogram([2], onClick));

    histogramGraph(svg, defaultGraphBounds(), histogram([0], onClick));
    const column = svg.querySelectorAll(".hover-columns rect")[2];
    column.dispatchEvent(new MouseEvent("click"));

    expect(column.classList.contains(clickableClass)).toBe(false);
    expect(onClick).not.toHaveBeenCalled();
});
