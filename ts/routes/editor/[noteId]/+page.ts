// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { PageLoad } from "./$types";

export const load = (async ({ params }) => {
    const noteId = BigInt(params.noteId);

    return { noteId };
}) satisfies PageLoad;
