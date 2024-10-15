// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
import type { GraphsResponse } from "@generated/anki/stats_pb";
import * as tr from "@generated/ftl";
import { localizedNumber } from "@tslib/i18n";
import { RevlogRange } from "./graph-helpers";

interface TrueRetentionData {
    youngPassed: number;
    youngFailed: number;
    maturePassed: number;
    matureFailed: number;
}

function calculateRetention(passed: number, failed: number): string {
    const total = passed + failed;
    if (total === 0) {
        return "0%";
    }
    return localizedNumber((passed / total) * 100, 1) + "%";
}

enum Scope {
    Young,
    Mature,
    Total,
}

function createStatsRow(period: string, data: TrueRetentionData, scope: Scope): string {
    let pass: number, fail: number, retention: string;
    switch (scope) {
        case Scope.Young:
            pass = data.youngPassed;
            fail = data.youngFailed;
            retention = calculateRetention(data.youngPassed, data.youngFailed);
            break;
        case Scope.Mature:
            pass = data.maturePassed;
            fail = data.matureFailed;
            retention = calculateRetention(data.maturePassed, data.matureFailed);
            break;
        case Scope.Total:
            pass = data.youngPassed + data.maturePassed;
            fail = data.youngFailed + data.matureFailed;
            retention = calculateRetention(pass, fail);
            break;
    }

    return `
        <tr>
            <td class="trl">${period}</td>
            <td class="trr">${localizedNumber(pass)}</td>
            <td class="trr">${localizedNumber(fail)}</td>
            <td class="trr">${retention}</td>
        </tr>`;
}

export function renderTrueRetention(data: GraphsResponse, revlogRange: RevlogRange): string {
    const trueRetention = data.trueRetention!;
    let output = "";
    for (const scope of Object.values(Scope)) {
        if (typeof scope === "string") { continue; }
        const tableContent = `
        <style>
            td.trl { border: 1px solid; text-align: left; }
            td.trr { border: 1px solid; text-align: right; }
            td.trc { border: 1px solid; text-align: center; }
            table.true-retention { width: 100%; table-layout: fixed; }
            colgroup col:first-child { width: 40% }
        </style>
        <table class="true-retention" cellspacing="0" cellpadding="2">
        <colgroup><col><col><col></colgroup>
            <tr>
                <td class="trl"><b>${scopeRange(scope)}</b></td>
                <td class="trr">${tr.statisticsTrueRetentionPass()}</td>
                <td class="trr">${tr.statisticsTrueRetentionFail()}</td>
                <td class="trr">${tr.statisticsTrueRetentionRetention()}</td>
            </tr>
            ${createStatsRow(tr.statisticsTrueRetentionToday(), trueRetention.today!, scope)}
            ${createStatsRow(tr.statisticsTrueRetentionYesterday(), trueRetention.yesterday!, scope)}
            ${createStatsRow(tr.statisticsTrueRetentionWeek(), trueRetention.week!, scope)}
            ${createStatsRow(tr.statisticsTrueRetentionMonth(), trueRetention.month!, scope)}
            ${
            revlogRange === RevlogRange.Year
                ? createStatsRow(tr.statisticsTrueRetentionYear(), trueRetention.year!, scope)
                : createStatsRow(tr.statisticsTrueRetentionAllTime(), trueRetention.allTime!, scope)
        }
        </table>`;
        output += tableContent;
    }

    return output;
}
function scopeRange(scope: Scope) {
    switch (scope) {
        case Scope.Young:
            return tr.statisticsCountsYoungCards();
        case Scope.Mature:
            return tr.statisticsCountsMatureCards();
        case Scope.Total:
            return tr.statisticsTotal();
    }
}
