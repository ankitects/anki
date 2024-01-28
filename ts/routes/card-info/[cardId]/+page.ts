import { cardStats } from "@generated/backend";
import type { PageLoad } from "./$types";

export const load = (async ({ fetch, url, params }) => {
    const info = await cardStats({ cid: BigInt(params.cardId) });
    return { info };
}) satisfies PageLoad;
