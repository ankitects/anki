// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { Stats } from "../lib/proto";
import { postRequest } from "../lib/postrequest";

export async function getCardStats(cardId: number): Promise<Stats.CardStatsResponse> {
    return Stats.CardStatsResponse.decode(
        await postRequest("/_anki/cardStats", JSON.stringify(cardId))
    );
}
