// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
import { getNotesToExport } from "@generated/backend";

import type { PageLoad } from "./$types";

export const load = (async (_) => {
    const noteIds = await getNotesToExport({});
    return { noteIds };
}) satisfies PageLoad;
