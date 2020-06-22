// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/* eslint
@typescript-eslint/no-non-null-assertion: "off",
@typescript-eslint/no-explicit-any: "off",
@typescript-eslint/ban-ts-ignore: "off" */

import pb from "../backend/proto";
import style from "./graphs.css";

async function fetchData(): Promise<Uint8Array> {
    let t = performance.now();
    const resp = await fetch("/_anki/graphData", {
        method: "POST",
    });
    if (!resp.ok) {
        throw Error(`unexpected reply: ${resp.statusText}`);
    }
    console.log(`fetch in ${performance.now() - t}ms`);
    t = performance.now();
    // get returned bytes
    const respBlob = await resp.blob();
    const respBuf = await new Response(respBlob).arrayBuffer();
    const bytes = new Uint8Array(respBuf);
    console.log(`bytes in ${performance.now() - t}ms`);
    t = performance.now();
    return bytes;
}

import IntervalGraph from "./IntervalsGraph.svelte";

export async function renderGraphs(): Promise<void> {
    const bytes = await fetchData();
    let t = performance.now();
    const data = pb.BackendProto.GraphsOut.decode(bytes);
    console.log(`decode in ${performance.now() - t}ms`);
    t = performance.now();

    document.head.append(style);

    new IntervalGraph({
        target: document.getElementById("svelte")!,
        props: { cards: data.cards2 },
    });
}
