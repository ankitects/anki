// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/* eslint
@typescript-eslint/no-non-null-assertion: "off",
@typescript-eslint/no-explicit-any: "off",
@typescript-eslint/ban-ts-ignore: "off" */

import pb from "../backend/proto";

async function fetchData(search: string, days: number): Promise<Uint8Array> {
    const resp = await fetch("/_anki/graphData", {
        method: "POST",
        body: JSON.stringify({
            search,
            days,
        }),
    });
    if (!resp.ok) {
        throw Error(`unexpected reply: ${resp.statusText}`);
    }
    // get returned bytes
    const respBlob = await resp.blob();
    const respBuf = await new Response(respBlob).arrayBuffer();
    const bytes = new Uint8Array(respBuf);
    return bytes;
}

export async function getGraphData(
    search: string,
    days: number
): Promise<pb.BackendProto.GraphsOut> {
    const bytes = await fetchData(search, days);
    return pb.BackendProto.GraphsOut.decode(bytes);
}

export enum GraphRange {
    Month = 1,
    Year = 2,
    All = 3,
}
