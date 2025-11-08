// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
export enum PercentageRangeEnum {
    All = 0,
    Percentile100 = 1,
    Percentile95 = 2,
    Percentile50 = 3,
}

export function PercentageRangeToQuantile(range: PercentageRangeEnum) {
    return ({
        [PercentageRangeEnum.Percentile100]: 1,
        [PercentageRangeEnum.Percentile95]: 0.95,
        [PercentageRangeEnum.Percentile50]: 0.5,
        [PercentageRangeEnum.All]: undefined,
    })[range];
}
