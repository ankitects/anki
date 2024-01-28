import { getChangeNotetypeInfo, getNotetypeNames } from "@generated/backend";
import { ChangeNotetypeState } from "../lib";
import type { PageLoad } from "./$types";

export const load = (async ({ fetch, url, params }) => {
    const [fromIdStr, toIdStr] = params.notetypeIds.split("/");
    const oldNotetypeId = BigInt(fromIdStr);
    const newNotetypeId = toIdStr ? BigInt(toIdStr) : oldNotetypeId;

    const notetypeNames = getNotetypeNames({});
    const changeNotetypeInfo = getChangeNotetypeInfo({
        oldNotetypeId,
        newNotetypeId,
    });
    const [names, info] = await Promise.all([notetypeNames, changeNotetypeInfo]);
    const state = new ChangeNotetypeState(names, info);

    return { state };
}) satisfies PageLoad;
