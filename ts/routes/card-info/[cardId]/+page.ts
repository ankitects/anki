// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
import { cardStats } from "@generated/backend";

import type { PageLoad } from "./$types";

export const load = (async ({ params }) => {
    const info = await cardStats({ cid: BigInt(params.cardId) });
    return { info };
}) satisfies PageLoad;
