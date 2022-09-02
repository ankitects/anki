// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { postRequest } from "../lib/postrequest";
import { Scheduler } from "../lib/proto";

async function getCustomScheduling(): Promise<Scheduler.CustomScheduling> {
    return Scheduler.CustomScheduling.decode(
        await postRequest("/_anki/getCustomScheduling", ""),
    );
}

async function setCustomScheduling(
    key: string,
    scheduling: Scheduler.CustomScheduling,
): Promise<void> {
    const bytes = Scheduler.CustomScheduling.encode(scheduling).finish();
    await postRequest("/_anki/setCustomScheduling", bytes, { key });
}

export async function mutateNextCardStates(
    key: string,
    mutator: (
        states: Scheduler.NextCardStates,
        customData: Record<string, unknown>,
    ) => void,
): Promise<void> {
    const scheduling = await getCustomScheduling();
    let customData = {};
    try {
        customData = JSON.parse(scheduling.customData);
    } catch {
        // can't be parsed
    }

    mutator(scheduling.states!, customData);

    scheduling.customData = JSON.stringify(customData);

    await setCustomScheduling(key, scheduling);
}
