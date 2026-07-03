// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { cardStats } from "@generated/backend";

import type { PageLoad } from "./$types";

function optionalBigInt(x: any): bigint | null {
    try {
        return BigInt(x);
    } catch (e) {
        return null;
    }
}

export const load = (async ({ params }) => {
    const currentId = optionalBigInt(params.cardId);
    const currentInfo = currentId !== null ? await cardStats({ cid: currentId }) : null;
    const previousId = optionalBigInt(params.previousId);
    const previousInfo = previousId !== null ? await cardStats({ cid: previousId }) : null;
    return { currentInfo, previousInfo };
}) satisfies PageLoad;
