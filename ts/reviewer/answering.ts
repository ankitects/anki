// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { cloneDeep } from "lodash-es";

import { postRequest } from "../lib/postrequest";
import { Scheduler } from "../lib/proto";

interface CustomDataStates {
    again: Record<string, unknown>;
    hard: Record<string, unknown>;
    good: Record<string, unknown>;
    easy: Record<string, unknown>;
}

async function getCustomScheduling(): Promise<Scheduler.CurrentCustomScheduling> {
    return Scheduler.CurrentCustomScheduling.decode(
        await postRequest("/_anki/getCustomScheduling", ""),
    );
}

async function setCustomScheduling(
    key: string,
    scheduling: Scheduler.NextCustomScheduling,
): Promise<void> {
    const bytes = Scheduler.NextCustomScheduling.encode(scheduling).finish();
    await postRequest("/_anki/setCustomScheduling", bytes, { key });
}

function buildCustomDataStates(serializedCustomData: string): CustomDataStates {
    let currentCustomData = {};
    try {
        currentCustomData = JSON.parse(serializedCustomData);
    } catch {
        // can't be parsed
    }
    const customData = {
        again: cloneDeep(currentCustomData),
        hard: cloneDeep(currentCustomData),
        good: cloneDeep(currentCustomData),
        easy: currentCustomData,
    };
    return customData;
}

function buildNextCustomScheduling(
    states: Scheduler.NextCardStates,
    customData: CustomDataStates,
): Scheduler.NextCustomScheduling {
    return Scheduler.NextCustomScheduling.create({
        states,
        againCustomData: JSON.stringify(customData.again),
        hardCustomData: JSON.stringify(customData.hard),
        goodCustomData: JSON.stringify(customData.good),
        easyCustomData: JSON.stringify(customData.easy),
    });
}

export async function mutateNextCardStates(
    key: string,
    mutator: (states: Scheduler.NextCardStates, customData: CustomDataStates) => void,
): Promise<void> {
    const scheduling = await getCustomScheduling();
    const customData = buildCustomDataStates(scheduling.customData);
    mutator(scheduling.states!, customData);
    const nextScheduling = buildNextCustomScheduling(scheduling.states!, customData);
    await setCustomScheduling(key, nextScheduling);
}
