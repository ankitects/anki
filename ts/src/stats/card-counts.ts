// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import pb from "../backend/proto";
import { CardQueue } from "../cards";
import { I18n } from "../i18n";

type Count = [string, number];
export interface CardCounts {
    title: string;
    counts: Count[];
}

export function gatherData(data: pb.BackendProto.GraphsOut, i18n: I18n): CardCounts {
    const totalCards = data.cards.length;
    let newCards = 0;
    let young = 0;
    let mature = 0;
    let suspended = 0;
    let buried = 0;

    for (const card of data.cards as pb.BackendProto.Card[]) {
        switch (card.queue) {
            case CardQueue.New:
                newCards += 1;
                break;
            case CardQueue.Review:
                if (card.ivl >= 21) {
                    mature += 1;
                    break;
                }
            // young falls through
            case CardQueue.Learn:
            case CardQueue.DayLearn:
                young += 1;
                break;
            case CardQueue.Suspended:
                suspended += 1;
                break;
            case CardQueue.SchedBuried:
            case CardQueue.UserBuried:
                buried += 1;
                break;
        }
    }

    const counts = [
        [i18n.tr(i18n.TR.STATISTICS_COUNTS_TOTAL_CARDS), totalCards] as Count,
        [i18n.tr(i18n.TR.STATISTICS_COUNTS_NEW_CARDS), newCards] as Count,
        [i18n.tr(i18n.TR.STATISTICS_COUNTS_YOUNG_CARDS), young] as Count,
        [i18n.tr(i18n.TR.STATISTICS_COUNTS_MATURE_CARDS), mature] as Count,
        [i18n.tr(i18n.TR.STATISTICS_COUNTS_SUSPENDED_CARDS), suspended] as Count,
        [i18n.tr(i18n.TR.STATISTICS_COUNTS_BURIED_CARDS), buried] as Count,
    ];

    return {
        title: i18n.tr(i18n.TR.STATISTICS_COUNTS_TITLE),
        counts,
    };
}
