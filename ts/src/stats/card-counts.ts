// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import pb from "../backend/proto";
import { CardQueue } from "../cards";

export interface CardCounts {
    totalCards: number;
    totalNotes: number;
    newCards: number;
    young: number;
    mature: number;
    suspended: number;
    buried: number;
}

export function gatherData(data: pb.BackendProto.GraphsOut): CardCounts {
    const totalNotes = data.noteCount;
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

    return {
        totalCards,
        totalNotes,
        newCards,
        young,
        mature,
        suspended,
        buried,
    };
}
