// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { GraphsResponse } from "@generated/anki/stats_pb";
import * as tr from "@generated/ftl";
import { localizedNumber } from "@tslib/i18n";
import { studiedToday } from "@tslib/time";

export interface TodayData {
    title: string;
    lines: string[];
}

export function gatherData(data: GraphsResponse): TodayData {
    let lines: string[];
    const today = data.today!;
    if (today.answerCount) {
        const studiedTodayText = studiedToday(today.answerCount, today.answerMillis / 1000);
        const againCount = today.answerCount - today.correctCount;
        let againCountText = tr.statisticsTodayAgainCount();
        againCountText += ` ${againCount} (${
            localizedNumber(
                (againCount / today.answerCount) * 100,
            )
        }%)`;
        const typeCounts = tr.statisticsTodayTypeCounts({
            learnCount: today.learnCount,
            reviewCount: today.reviewCount,
            relearnCount: today.relearnCount,
            filteredCount: today.earlyReviewCount,
        });
        let matureText: string;
        if (today.matureCount) {
            matureText = tr.statisticsTodayCorrectMature({
                correct: today.matureCorrect,
                total: today.matureCount,
                percent: (today.matureCorrect / today.matureCount) * 100,
            });
        } else {
            matureText = tr.statisticsTodayNoMatureCards();
        }
        lines = [studiedTodayText, againCountText, typeCounts, matureText];
    } else {
        lines = [tr.statisticsTodayNoCards()];
    }

    return {
        title: tr.statisticsTodayTitle(),
        lines,
    };
}
