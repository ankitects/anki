// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { Scheduler } from "lib/proto";
import { postRequest } from "lib/postrequest";

async function getNextStates(): Promise<Scheduler.NextCardStates> {
    return Scheduler.NextCardStates.decode(
        await postRequest("/_anki/nextCardStates", "")
    );
}

async function setNextStates(
    key: string,
    states: Scheduler.NextCardStates
): Promise<void> {
    const data: Uint8Array = Scheduler.NextCardStates.encode(states).finish();
    await postRequest("/_anki/setNextCardStates", data, { key });
}

export async function mutateNextCardStates(
    key: string,
    mutator: (states: Scheduler.NextCardStates) => void
): Promise<void> {
    const states = await getNextStates();
    mutator(states);
    await setNextStates(key, states);
}
