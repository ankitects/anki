// Copyright: Ankitects Pty Ltd and contributors

import { sum } from "d3";

// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
export enum PercentageRangeEnum {
    All = 0,
    Percentile100 = 1,
    Percentile95 = 2,
}

export function PercentageRangeToQuantile(range: PercentageRangeEnum) {
    return ({
        [PercentageRangeEnum.Percentile100]: 1,
        [PercentageRangeEnum.Percentile95]: 0.975, // this is halved because the quantiles are in both directions
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
