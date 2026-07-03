// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
import { getImportAnkiPackagePresets } from "@generated/backend";

import type { PageLoad } from "./$types";

export const load = (async ({ params }) => {
    const options = await getImportAnkiPackagePresets({});
    return { path: params.path, options };
}) satisfies PageLoad;
