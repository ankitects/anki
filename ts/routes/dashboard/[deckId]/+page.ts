// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
import type { PageLoad } from "./$types";

export const load = (({ params }) => {
    let deckId = 0n;
    try {
        deckId = BigInt(params.deckId);
    } catch (e) {
        deckId = 0n;
    }
    return { deckId };
}) satisfies PageLoad;
