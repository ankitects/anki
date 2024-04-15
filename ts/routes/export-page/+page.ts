// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
import { getDeckNames } from "@generated/backend";

import type { PageLoad } from "./$types";

export const load = (async (_) => {
    const deckNames = await getDeckNames({ skipEmptyDefault: false, includeFiltered: true });
    return { deckNames };
}) satisfies PageLoad;
