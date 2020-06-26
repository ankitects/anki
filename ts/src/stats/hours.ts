// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import pb from "../backend/proto";

type ButtonCounts = [number, number, number, number];

interface Hour {
    hour: number;
    totalCount: number;
    correctCount: number;
}

export interface GraphData {
    hours: Hour[];
}

const ReviewKind = pb.BackendProto.RevlogEntry.ReviewKind;

export function gatherData(data: pb.BackendProto.GraphsOut): GraphData {
    const hours = Array(24).map((n: number) => {
        return { hour: n, totalCount: 0, correctCount: 0 } as Hour;
    });

    // fixme: relative to midnight, not rollover

    for (const review of data.revlog as pb.BackendProto.RevlogEntry[]) {
        if (review.reviewKind == ReviewKind.EARLY_REVIEW) {
            continue;
        }

        const hour =
            (((review.id as number) / 1000 + data.localOffsetSecs) / 3600) % 24;
        hours[hour].totalCount += 1;
        if (review.buttonChosen != 1) {
            hours[hour].correctCount += 1;
        }
    }

    return { hours };
}
