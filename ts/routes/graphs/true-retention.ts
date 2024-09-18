// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
import type { GraphsResponse } from "@generated/anki/stats_pb";
import { localizedNumber } from "@tslib/i18n";

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
            <td class="trr"><span class="young">${localizedNumber(data.youngPassed)}</span></td>
            <td class="trr"><span class="young">${localizedNumber(data.youngFailed)}</span></td>
            <td class="trr"><span class="young">${youngRetention}</span></td>
            <td class="trr"><span class="mature">${localizedNumber(data.maturePassed)}</span></td>
            <td class="trr"><span class="mature">${localizedNumber(data.matureFailed)}</span></td>
            <td class="trr"><span class="mature">${matureRetention}</span></td>
            <td class="trr"><span class="total">${localizedNumber(totalPassed)}</span></td>
            <td class="trr"><span class="total">${localizedNumber(totalFailed)}</span></td>
            <td class="trr"><span class="total">${totalRetention}</span></td>
            <td class="trr"><span class="young">${localizedNumber(data.learned)}</span></td>
            <td class="trr"><span class="relearn">${localizedNumber(data.relearned)}</span></td>
        </tr>`;
}

export function renderTrueRetention(data: GraphsResponse): string {
    const trueRetention = data.trueRetention!;
    const matureIvl = 21;

    const tableContent = `
        <style>
            td.trl { border: 1px solid; text-align: left; padding: 5px  }
            td.trr { border: 1px solid; text-align: right; padding: 5px  }
            td.trc { border: 1px solid; text-align: center; padding: 5px }
            span.young { color: #77cc77 }
            span.mature { color: #00aa00 }
            span.total { color: #55aa55 }
            span.relearn { color: #c35617 }
        </style>
        <table style="border-collapse: collapse;" cellspacing="0" cellpadding="2">
            <tr>
                <td class="trl" rowspan=3><b>Range</b></td>
                <td class="trc" colspan=9><b>Reviews on Cards</b></td>
                <td class="trc" colspan=2 valign=middle><b>Cards</b></td>
            </tr>
            <tr>
                <td class="trc" colspan=3><span class="young"><b>Young (ivl < ${matureIvl} d)</b></span></td>
                <td class="trc" colspan=3><span class="mature"><b>Mature (ivl ≥ ${matureIvl} d)</b></span></td>
                <td class="trc" colspan=3><span class="total"><b>Total</b></span></td>
                <td class="trc" rowspan=2><span class="young"><b>Learned</b></span></td>
                <td class="trc" rowspan=2><span class="relearn"><b>Relearned</b></span></td>
            </tr>
            <tr>
                <td class="trc"><span class="young">Pass</span></td>
                <td class="trc"><span class="young">Fail</span></td>
                <td class="trc"><span class="young">Retention</span></td>
                <td class="trc"><span class="mature">Pass</span></td>
                <td class="trc"><span class="mature">Fail</span></td>
                <td class="trc"><span class="mature">Retention</span></td>
                <td class="trc"><span class="total">Pass</span></td>
                <td class="trc"><span class="total">Fail</span></td>
                <td class="trc"><span class="total">Retention</span></td>
            </tr>
            ${createStatsRow("Today", trueRetention.today!)}
            ${createStatsRow("Yesterday", trueRetention.yesterday!)}
            ${createStatsRow("Week", trueRetention.week!)}
            ${createStatsRow("Month", trueRetention.month!)}
            ${createStatsRow("Year", trueRetention.year!)}
            ${createStatsRow("All Time", trueRetention.allTime!)}
        </table>
        <p>The True Retention is the pass rate calculated only on cards with intervals greater than or equal to one day. It is a better indicator of the learning quality than the Again rate.</p>`;

    return tableContent;
}
