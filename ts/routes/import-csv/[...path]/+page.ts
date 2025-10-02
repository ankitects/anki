// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
import { getCsvMetadata, getDeckNames, getNotetypeNames } from "@generated/backend";

import { ImportCsvState } from "../lib";
import type { PageLoad } from "./$types";

export const load = (async ({ params }) => {
    try {
        const [notetypes, decks, metadata] = await Promise.all([
            getNotetypeNames({}),
            getDeckNames({
                skipEmptyDefault: false,
                includeFiltered: false,
            }),
            getCsvMetadata({ path: params.path }, { alertOnError: false }),
        ]);

        const state = new ImportCsvState(params.path, notetypes, decks, metadata);
        return { state };
    } catch (e: any) {
        const rawMsg = e?.message ?? String(e ?? "");
        return { initialError: new Error(rawMsg) };
    }
}) satisfies PageLoad;
