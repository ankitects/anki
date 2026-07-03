// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
import * as tr from "@generated/ftl";
import { createLocaleNumberFormat } from "@tslib/i18n";
import { assertUnreachable } from "@tslib/typing";
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
            assertUnreachable(scope);
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
            assertUnreachable(scope);
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
            title: tr.statisticsTrueRetentionToday(),
            data: allData.today,
        },
        {
            title: tr.statisticsTrueRetentionYesterday(),
            data: allData.yesterday,
        },
        {
            title: tr.statisticsTrueRetentionWeek(),
            data: allData.week,
        },
        {
            title: tr.statisticsTrueRetentionMonth(),
            data: allData.month,
        },
        {
            title: tr.statisticsTrueRetentionYear(),
            data: allData.year,
        },
    ];

    if (revlogRange === RevlogRange.All) {
        rowData.push({
            title: tr.statisticsTrueRetentionAllTime(),
            data: allData.allTime,
        });
    }

    return rowData;
}

export function calculateRetentionPercentageString(
    passed: number,
    failed: number,
): string {
    const total = passed + failed;

    if (total === 0) {
        return tr.statisticsTrueRetentionNotApplicable();
    }

    const numberFormat = createLocaleNumberFormat({
        minimumFractionDigits: 1,
        maximumFractionDigits: 1,
        style: "percent",
    });

    return numberFormat.format(passed / total);
}
