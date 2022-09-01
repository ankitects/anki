// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { postRequest } from "../lib/postrequest";
import { Scheduler } from "../lib/proto";

async function getNextStates(): Promise<Scheduler.NextCardStates> {
    return Scheduler.NextCardStates.decode(
        await postRequest("/_anki/nextCardStates", ""),
    );
}

async function setNextStates(
    key: string,
    states: Scheduler.NextCardStates,
): Promise<void> {
    const data: Uint8Array = Scheduler.NextCardStates.encode(states).finish();
    await postRequest("/_anki/setNextCardStates", data, { key });
}

async function getCardMeta(): Promise<Record<string, unknown>> {
    const bytes = await postRequest("/_anki/getCardMeta", "");
    const str = new TextDecoder().decode(bytes);
    return JSON.parse(str);
}

async function setCardMeta(key: string, meta: Record<string, unknown>): Promise<void> {
    const bytes = new TextEncoder().encode(JSON.stringify(meta));
    await postRequest("/_anki/setCardMeta", bytes, { key });
}

export async function mutateNextCardStates(
    key: string,
    mutator: (
        states: Scheduler.NextCardStates,
        customData: Record<string, unknown>,
    ) => void,
): Promise<void> {
    const [states, customData] = await Promise.all([getNextStates(), getCardMeta()]);
    mutator(states, customData);
    await Promise.all([setNextStates(key, states), setCardMeta(key, customData)]);
}
