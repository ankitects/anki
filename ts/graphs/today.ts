// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import pb from "anki/backend_proto";
import { studiedToday } from "anki/time";
import type { I18n } from "anki/i18n";

export interface TodayData {
    title: string;
    lines: string[];
}

const ReviewKind = pb.BackendProto.RevlogEntry.ReviewKind;

export function gatherData(data: pb.BackendProto.GraphsOut, i18n: I18n): TodayData {
    let answerCount = 0;
    let answerMillis = 0;
    let correctCount = 0;
    let matureCorrect = 0;
    let matureCount = 0;
    let learnCount = 0;
    let reviewCount = 0;
    let relearnCount = 0;
    let earlyReviewCount = 0;

    const startOfTodayMillis = (data.nextDayAtSecs - 86400) * 1000;

    for (const review of data.revlog as pb.BackendProto.RevlogEntry[]) {
        if (review.id < startOfTodayMillis) {
            continue;
        }

        if (review.reviewKind == ReviewKind.MANUAL) {
            continue;
        }

        // total
        answerCount += 1;
        answerMillis += review.takenMillis;

        // correct
        if (review.buttonChosen > 1) {
            correctCount += 1;
        }

        // mature
        if (review.lastInterval >= 21) {
            matureCount += 1;
            if (review.buttonChosen > 1) {
                matureCorrect += 1;
            }
        }

        // type counts
        switch (review.reviewKind) {
            case ReviewKind.LEARNING:
                learnCount += 1;
                break;
            case ReviewKind.REVIEW:
                reviewCount += 1;
                break;
            case ReviewKind.RELEARNING:
                relearnCount += 1;
                break;
            case ReviewKind.EARLY_REVIEW:
                earlyReviewCount += 1;
                break;
        }
    }

    let lines: string[];
    if (answerCount) {
        const studiedTodayText = studiedToday(i18n, answerCount, answerMillis / 1000);
        const againCount = answerCount - correctCount;
        let againCountText = i18n.tr(i18n.TR.STATISTICS_TODAY_AGAIN_COUNT);
        againCountText += ` ${againCount} (${((againCount / answerCount) * 100).toFixed(
            2
        )}%)`;
        const typeCounts = i18n.tr(i18n.TR.STATISTICS_TODAY_TYPE_COUNTS, {
            learnCount,
            reviewCount,
            relearnCount,
            filteredCount: earlyReviewCount,
        });
        let matureText: string;
        if (matureCount) {
            matureText = i18n.tr(i18n.TR.STATISTICS_TODAY_CORRECT_MATURE, {
                correct: matureCorrect,
                total: matureCount,
                percent: (matureCorrect / matureCount) * 100,
            });
        } else {
            matureText = i18n.tr(i18n.TR.STATISTICS_TODAY_NO_MATURE_CARDS);
        }

        lines = [studiedTodayText, againCountText, typeCounts, matureText];
    } else {
        lines = [i18n.tr(i18n.TR.STATISTICS_TODAY_NO_CARDS)];
    }

    return {
        title: i18n.tr(i18n.TR.STATISTICS_TODAY_TITLE),
        lines,
    };
}
