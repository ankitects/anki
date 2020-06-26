// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import pb from "../backend/proto";

export interface TodayData {
    answerCount: number;
    answerMillis: number;
    correctCount: number;
    matureCorrect: number;
    matureCount: number;
    learnCount: number;
    reviewCount: number;
    relearnCount: number;
    earlyReviewCount: number;
}

const ReviewKind = pb.BackendProto.RevlogEntry.ReviewKind;

export function gatherData(data: pb.BackendProto.GraphsOut): TodayData {
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

    return {
        answerCount,
        answerMillis,
        correctCount,
        matureCorrect,
        matureCount,
        learnCount,
        reviewCount,
        relearnCount,
        earlyReviewCount,
    };
}
