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
    learned: number;
    relearned: number;
}

function calculateRetention(passed: number, failed: number): string {
    const total = passed + failed;
    if (total === 0) {
        return "0%";
    }
    return localizedNumber((passed / total) * 100) + "%";
}

function createStatsRow(name: string, data: TrueRetentionData): string {
    const youngRetention = calculateRetention(data.youngPassed, data.youngFailed);
    const matureRetention = calculateRetention(data.maturePassed, data.matureFailed);
    const totalPassed = data.youngPassed + data.maturePassed;
    const totalFailed = data.youngFailed + data.matureFailed;
    const totalRetention = calculateRetention(totalPassed, totalFailed);

    return `
        <tr>
            <td class="trl">${name}</td>
            <td class="trr">${localizedNumber(data.youngPassed)}</td>
            <td class="trr">${localizedNumber(data.youngFailed)}</td>
            <td class="trr">${youngRetention}</td>
            <td class="trr">${localizedNumber(data.maturePassed)}</td>
            <td class="trr">${localizedNumber(data.matureFailed)}</td>
            <td class="trr">${matureRetention}</td>
            <td class="trr">${localizedNumber(totalPassed)}</td>
            <td class="trr">${localizedNumber(totalFailed)}</td>
            <td class="trr">${totalRetention}</td>
            <td class="trr">${localizedNumber(data.learned)}</td>
            <td class="trr">${localizedNumber(data.relearned)}</td>
        </tr>`;
}

export function renderTrueRetention(data: GraphsResponse, revlogRange: RevlogRange): string {
    const trueRetention = data.trueRetention!;

    const tableContent = `
        <style>
            td.trl { border: 1px solid; text-align: left; padding: 5px; }
            td.trr { border: 1px solid; text-align: right; padding: 5px; }
            td.trc { border: 1px solid; text-align: center; padding: 5px; }
        </style>
        <table style="border-collapse: collapse;" cellspacing="0" cellpadding="2">
            <tr>
                <td class="trl" rowspan=3><b>${tr.statisticsTrueRetentionRange()}</b></td>
                <td class="trc" colspan=9><b>${tr.statisticsReviewsTitle()}</b></td>
                <td class="trc" colspan=2 valign=middle><b>${tr.statisticsCountsTitle()}</b></td>
            </tr>
            <tr>
                <td class="trc" colspan=3><b>${tr.statisticsCountsYoungCards()}</b></td>
                <td class="trc" colspan=3><b>${tr.statisticsCountsMatureCards()}</b></td>
                <td class="trc" colspan=3><b>${tr.statisticsCountsTotalCards()}</b></td>
                <td class="trc" rowspan=2><b>${tr.statisticsCountsLearningCards()}</b></td>
                <td class="trc" rowspan=2><b>${tr.statisticsCountsRelearningCards()}</b></td>
            </tr>
            <tr>
                <td class="trc">${tr.statisticsTrueRetentionPass()}</td>
                <td class="trc">${tr.statisticsTrueRetentionFail()}</td>
                <td class="trc">${tr.statisticsTrueRetentionRetention()}</td>
                <td class="trc">${tr.statisticsTrueRetentionPass()}</td>
                <td class="trc">${tr.statisticsTrueRetentionFail()}</td>
                <td class="trc">${tr.statisticsTrueRetentionRetention()}</td>
                <td class="trc">${tr.statisticsTrueRetentionPass()}</td>
                <td class="trc">${tr.statisticsTrueRetentionFail()}</td>
                <td class="trc">${tr.statisticsTrueRetentionRetention()}</td>
            </tr>
            ${createStatsRow(tr.statisticsTodayTitle(), trueRetention.today!)}
            ${createStatsRow(tr.statisticsDaysAgoSingle({ days: 1 }), trueRetention.yesterday!)}
            ${createStatsRow(tr.statisticsDaysAgoSingle({ days: 7 }), trueRetention.week!)}
            ${createStatsRow(tr.statisticsDaysAgoSingle({ days: 30 }), trueRetention.month!)}
            ${
        revlogRange === RevlogRange.Year
            ? createStatsRow(tr.statisticsRange1YearHistory(), trueRetention.year!)
            : createStatsRow(tr.statisticsRangeAllHistory(), trueRetention.allTime!)
    }
        </table>`;

    return tableContent;
}
