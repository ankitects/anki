// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { postRequest } from "@tslib/postrequest";
import { Scheduler } from "@tslib/proto";

interface CustomDataStates {
    again: Record<string, unknown>;
    hard: Record<string, unknown>;
    good: Record<string, unknown>;
    easy: Record<string, unknown>;
}

async function getSchedulingStatesWithContext(): Promise<Scheduler.SchedulingStatesWithContext> {
    return Scheduler.SchedulingStatesWithContext.decode(
        await postRequest("/_anki/getSchedulingStatesWithContext", ""),
    );
}

async function setSchedulingStates(
    key: string,
    states: Scheduler.SchedulingStates,
): Promise<void> {
    const bytes = Scheduler.SchedulingStates.encode(states).finish();
    await postRequest("/_anki/setSchedulingStates", bytes, { key });
}

function unpackCustomData(states: Scheduler.SchedulingStates): CustomDataStates {
    const toObject = (s: string): Record<string, unknown> => {
        try {
            return JSON.parse(s);
        } catch {
            return {};
        }
    };
    return {
        again: toObject(states.current!.customData!),
        hard: toObject(states.current!.customData!),
        good: toObject(states.current!.customData!),
        easy: toObject(states.current!.customData!),
    };
}

function packCustomData(
    states: Scheduler.SchedulingStates,
    customData: CustomDataStates,
) {
    states.again!.customData = JSON.stringify(customData.again);
    states.hard!.customData = JSON.stringify(customData.hard);
    states.good!.customData = JSON.stringify(customData.good);
    states.easy!.customData = JSON.stringify(customData.easy);
}

export async function mutateNextCardStates(
    key: string,
    mutator: (
        states: Scheduler.SchedulingStates,
        customData: CustomDataStates,
        ctx: Scheduler.SchedulingContext,
    ) => Promise<void>,
): Promise<void> {
    const statesWithContext = await getSchedulingStatesWithContext();
    const states = statesWithContext.states!;
    const customData = unpackCustomData(states);
    await mutator(states, customData, statesWithContext.context!);
    packCustomData(states, customData);
    await setSchedulingStates(key, states);
}
