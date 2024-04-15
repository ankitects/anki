// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
import { getDeckNames } from "@generated/backend";

import type { PageLoad } from "./$types";

export const load = (async ({ params }) => {
    const deckNames = await getDeckNames({ skipEmptyDefault: false, includeFiltered: true });
    console.log(BigInt(params.deckId));
    return { deckId: BigInt(params.deckId), deckNames };
}) satisfies PageLoad;
