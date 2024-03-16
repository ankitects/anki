import type { PageLoad } from "./$types";

export const load = (async ({ fetch, url, params }) => {
    return { path: params.path };
}) satisfies PageLoad;
