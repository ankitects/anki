import { congratsInfo } from "@generated/backend";
import type { PageLoad } from "./$types";

export const load = (async ({ fetch, url, params }) => {
    const info = await congratsInfo({});
    return { info };
}) satisfies PageLoad;
