// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import * as pb from "lib/backend_proto";
import { postRequest } from "lib/postrequest";

async function getNextStates(): Promise<pb.BackendProto.NextCardStates> {
    return pb.BackendProto.NextCardStates.decode(
        await postRequest("/_anki/nextCardStates", "")
    );
}

async function setNextStates(
    key: string,
    states: pb.BackendProto.NextCardStates
): Promise<void> {
    const data: Uint8Array = pb.BackendProto.NextCardStates.encode(states).finish();
    await postRequest("/_anki/setNextCardStates", data, { key });
}

export async function mutateNextCardStates(
    key: string,
    mutator: (states: pb.BackendProto.NextCardStates) => void
): Promise<void> {
    const states = await getNextStates();
    mutator(states);
    await setNextStates(key, states);
}
