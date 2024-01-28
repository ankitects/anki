import { getImportAnkiPackagePresets } from "@generated/backend";
import type { PageLoad } from "./$types";

export const load = (async ({ fetch, url, params }) => {
    const options = await getImportAnkiPackagePresets({});
    return { path: params.path, options };
}) satisfies PageLoad;
