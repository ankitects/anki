// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
import * as t9n from "@generated/ftl";
import { localizedNumber } from "@tslib/i18n";
import { RevlogRange } from "./graph-helpers";

export interface TrueRetentionData {
    youngPassed: number;
    youngFailed: number;
    maturePassed: number;
    matureFailed: number;
}

export interface PeriodTrueRetentionData {
    today: TrueRetentionData;
    yesterday: TrueRetentionData;
    week: TrueRetentionData;
    month: TrueRetentionData;
    year: TrueRetentionData;
    allTime: TrueRetentionData;
}

export enum DisplayMode {
    Young,
    Mature,
    All,
    Summary,
}

export enum Scope {
    Young,
    Mature,
    All,
}

export function getPassed(data: TrueRetentionData, scope: Scope): number {
    switch (scope) {
        case Scope.Young:
            return data.youngPassed;
        case Scope.Mature:
            return data.maturePassed;
        case Scope.All:
            return data.youngPassed + data.maturePassed;
        default:
            throw new Error(`Unexpected scope: ${scope}`);
    }
}

export function getFailed(data: TrueRetentionData, scope: Scope): number {
    switch (scope) {
        case Scope.Young:
            return data.youngFailed;
        case Scope.Mature:
            return data.matureFailed;
        case Scope.All:
            return data.youngFailed + data.matureFailed;
        default:
            throw new Error(`Unexpected scope: ${scope}`);
    }
}

export interface RowData {
    title: string;
    data: TrueRetentionData;
}

export function getRowData(
    allData: PeriodTrueRetentionData,
    revlogRange: RevlogRange,
): RowData[] {
    const rowData: RowData[] = [
        {
            title: t9n.statisticsTrueRetentionToday(),
            data: allData.today,
        },
        {
            title: t9n.statisticsTrueRetentionYesterday(),
            data: allData.yesterday,
        },
        {
            title: t9n.statisticsTrueRetentionWeek(),
            data: allData.week,
        },
        {
            title: t9n.statisticsTrueRetentionMonth(),
            data: allData.month,
        },
        {
            title: t9n.statisticsTrueRetentionYear(),
            data: allData.year,
        },
    ];

    if (revlogRange === RevlogRange.All) {
        rowData.push({
            title: t9n.statisticsTrueRetentionAllTime(),
            data: allData.allTime,
        });
    }

    return rowData;
}

export function calculateRetentionPercentageString(
    passed: number,
    failed: number,
): string {
    let percentage = 0;
    const total = passed + failed;

    if (total !== 0) {
        percentage = (passed / total) * 100;
    }

    return localizedNumber(percentage, 1) + "%";
}
