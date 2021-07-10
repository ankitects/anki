// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { Backend } from "lib/proto";
import { postRequest } from "lib/postrequest";

async function getNextStates(): Promise<Backend.NextCardStates> {
    return Backend.NextCardStates.decode(
        await postRequest("/_anki/nextCardStates", "")
    );
}

async function setNextStates(
    key: string,
    states: Backend.NextCardStates
): Promise<void> {
    const data: Uint8Array = Backend.NextCardStates.encode(states).finish();
    await postRequest("/_anki/setNextCardStates", data, { key });
}

export async function mutateNextCardStates(
    key: string,
    mutator: (states: Backend.NextCardStates) => void
): Promise<void> {
    const states = await getNextStates();
    mutator(states);
    await setNextStates(key, states);
}
