// Copyright: Ankitects Pty Ltd and contributors

import { range, type ScaleLinear, scaleLinear, sum } from "d3";

// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
export enum PercentageRangeEnum {
    All = 0,
    Percentile100 = 1,
    Percentile95 = 2,
    Percentile50 = 3,
}

export function PercentageRangeToQuantile(range: PercentageRangeEnum) {
    // These are halved because the quantiles are in both directions
    return ({
        [PercentageRangeEnum.Percentile100]: 1,
        [PercentageRangeEnum.Percentile95]: 0.975,
        [PercentageRangeEnum.Percentile50]: 0.75,
        [PercentageRangeEnum.All]: undefined,
    })[range];
}

export function easeQuantile(data: Map<number, number>, quantile: number) {
    let count = sum(data.values()) * quantile;
    for (const [key, value] of data.entries()) {
        count -= value;
        if (count <= 0) {
            return key;
        }
    }
}

export function percentageRangeMinMax(data: Map<number, number>, range: number | undefined) {
    const xMin = range ? easeQuantile(data, 1 - range) ?? 0 : 0;
    const xMax = range ? easeQuantile(data, range) ?? 0 : 100;

    return [xMin, xMax];
}

export function getAdjustedScaleAndTicks(
    min: number,
    max: number,
    desiredBars: number,
): [ScaleLinear<number, number, never>, number[]] {
    const prescale = scaleLinear().domain([min, max]).nice();
    let ticks = prescale.ticks(desiredBars);

    const predomain = prescale.domain() as [number, number];

    const minOffset = min - predomain[0];
    const tickSize = ticks[1] - ticks[0];

    if (tickSize < 1) {
        ticks = range(min, max);
    }

    if (minOffset === 0 || (minOffset % tickSize !== 0 && tickSize % minOffset !== 0)) {
        return [prescale, ticks];
    }

    const add = (n: number): number => n + minOffset;
    return [
        scaleLinear().domain(predomain.map(add) as [number, number]),
        ticks.map(add),
    ];
}
