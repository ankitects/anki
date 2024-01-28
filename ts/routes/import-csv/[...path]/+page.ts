import { getCsvMetadata, getDeckNames, getNotetypeNames } from "@generated/backend";
import { ImportCsvState } from "../lib";
import type { PageLoad } from "./$types";

export const load = (async ({ fetch, url, params }) => {
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
}) satisfies PageLoad;
