// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { JsonValue } from "@bufbuild/protobuf";
import type { SchedulingStatesWithContext } from "@generated/anki/frontend_pb";
import type { SchedulingContext } from "@generated/anki/scheduler_pb";
import { SchedulingStates } from "@generated/anki/scheduler_pb";
import { getSchedulingStatesWithContext, setSchedulingStates } from "@generated/backend";

interface CustomDataStates {
    again: Record<string, unknown>;
    hard: Record<string, unknown>;
    good: Record<string, unknown>;
    easy: Record<string, unknown>;
}

function unpackCustomData(states: SchedulingStates): CustomDataStates {
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
    states: SchedulingStates,
    customData: CustomDataStates,
) {
    states.again!.customData = JSON.stringify(customData.again);
    states.hard!.customData = JSON.stringify(customData.hard);
    states.good!.customData = JSON.stringify(customData.good);
    states.easy!.customData = JSON.stringify(customData.easy);
}

type StateMutatorFn = (states: JsonValue, customData: CustomDataStates, ctx: SchedulingContext) => Promise<void>;

export async function mutateNextCardStates(
    key: string,
    transform: StateMutatorFn,
): Promise<void> {
    const statesWithContext = await getSchedulingStatesWithContext({});
    const updatedStates = await applyStateTransform(statesWithContext, transform);
    await setSchedulingStates({ key, states: updatedStates });
}

/** Exported only for tests */
export async function applyStateTransform(
    states: SchedulingStatesWithContext,
    transform: StateMutatorFn,
): Promise<SchedulingStates> {
    // convert to JSON, which is the format existing transforms expect
    const statesJson = states.states!.toJson({ emitDefaultValues: true });

    // decode customData and put it into each state
    const customData = unpackCustomData(states.states!);

    // run the user function on the JSON
    await transform(statesJson, customData, states.context!);

    // convert the JSON back into proto form, and pack the custom data in
    const updatedStates = SchedulingStates.fromJson(statesJson);
    packCustomData(updatedStates, customData);

    return updatedStates;
}
