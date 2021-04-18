// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/* eslint
@typescript-eslint/no-explicit-any: "off",
 */

import * as pb from "anki/backend_proto";
import { DeckConfigState } from "./lib";
import { get } from "svelte/store";

const exampleData = {
    allConfig: [
        {
            config: {
                id: "1",
                name: "Default",
                mtimeSecs: "1618570764",
                usn: -1,
                config: {
                    learnSteps: [1, 10],
                    relearnSteps: [10],
                    newPerDay: 10,
                    reviewsPerDay: 200,
                    initialEase: 2.5,
                    easyMultiplier: 1.2999999523162842,
                    hardMultiplier: 1.2000000476837158,
                    intervalMultiplier: 1,
                    maximumReviewInterval: 36500,
                    minimumLapseInterval: 1,
                    graduatingIntervalGood: 1,
                    graduatingIntervalEasy: 4,
                    leechAction: "LEECH_ACTION_TAG_ONLY",
                    leechThreshold: 8,
                    capAnswerTimeToSecs: 60,
                },
            },
            useCount: 1,
        },
        {
            config: {
                id: "1618570764780",
                name: "another one",
                mtimeSecs: "1618570781",
                usn: -1,
                config: {
                    learnSteps: [1, 10, 20, 30],
                    relearnSteps: [10],
                    newPerDay: 40,
                    reviewsPerDay: 200,
                    initialEase: 2.5,
                    easyMultiplier: 1.2999999523162842,
                    hardMultiplier: 1.2000000476837158,
                    intervalMultiplier: 1,
                    maximumReviewInterval: 36500,
                    minimumLapseInterval: 1,
                    graduatingIntervalGood: 1,
                    graduatingIntervalEasy: 4,
                    leechAction: "LEECH_ACTION_TAG_ONLY",
                    leechThreshold: 8,
                    capAnswerTimeToSecs: 60,
                },
            },
            useCount: 1,
        },
    ],
    currentDeck: {
        name: "Default::child",
        configId: "1618570764780",
        parentConfigIds: [1],
    },
    defaults: {
        config: {
            learnSteps: [1, 10],
            relearnSteps: [10],
            newPerDay: 20,
            reviewsPerDay: 200,
            initialEase: 2.5,
            easyMultiplier: 1.2999999523162842,
            hardMultiplier: 1.2000000476837158,
            intervalMultiplier: 1,
            maximumReviewInterval: 36500,
            minimumLapseInterval: 1,
            graduatingIntervalGood: 1,
            graduatingIntervalEasy: 4,
            leechAction: "LEECH_ACTION_TAG_ONLY",
            leechThreshold: 8,
            capAnswerTimeToSecs: 60,
        },
    },
};

function startingState(): DeckConfigState {
    return new DeckConfigState(
        pb.BackendProto.DeckConfigsForUpdate.fromObject(exampleData)
    );
}

test("start", () => {
    const state = startingState();
    expect(state.currentDeck.name).toBe("Default::child");
});

test("deck list", () => {
    const state = startingState();
    expect(get(state.configList)).toStrictEqual([
        {
            current: true,
            idx: 1,
            name: "another one",
            useCount: 1,
        },
        {
            current: false,
            idx: 0,
            name: "Default",
            useCount: 1,
        },
    ]);
    expect(get(state.currentConfig).newPerDay).toBe(40);

    // rename
    state.setCurrentName("zzz");
    expect(get(state.configList)).toStrictEqual([
        {
            current: false,
            idx: 0,
            name: "Default",
            useCount: 1,
        },
        {
            current: true,
            idx: 1,
            name: "zzz",
            useCount: 1,
        },
    ]);

    // add
    state.addConfig("hello");
    expect(get(state.configList)).toStrictEqual([
        {
            current: false,
            idx: 0,
            name: "Default",
            useCount: 1,
        },
        {
            current: true,
            idx: 2,
            name: "hello",
            useCount: 1,
        },
        {
            current: false,
            idx: 1,
            name: "zzz",
            useCount: 0,
        },
    ]);
    expect(get(state.currentConfig).newPerDay).toBe(20);

    // change current
    state.setCurrentIndex(0);
    expect(get(state.configList)).toStrictEqual([
        {
            current: true,
            idx: 0,
            name: "Default",
            useCount: 2,
        },
        {
            current: false,
            idx: 2,
            name: "hello",
            useCount: 0,
        },
        {
            current: false,
            idx: 1,
            name: "zzz",
            useCount: 0,
        },
    ]);
    expect(get(state.currentConfig).newPerDay).toBe(10);

    // can't delete default
    expect(() => state.removeCurrentConfig()).toThrow();

    // deleting old deck should work
    state.setCurrentIndex(1);
    state.removeCurrentConfig();
    expect(get(state.currentConfig).newPerDay).toBe(10);

    // as should newly added one
    state.setCurrentIndex(1);
    state.removeCurrentConfig();
    expect(get(state.currentConfig).newPerDay).toBe(10);

    // only the pre-existing deck should be listed for removal
    expect((state as any).removedConfigs).toStrictEqual([1618570764780]);
});

test("duplicate name", () => {
    const state = startingState();

    // duplicate will get renamed
    state.addConfig("another one");
    expect(get(state.configList).find((e) => e.current)?.name).toMatch(/another.*\d+$/);

    // should handle renames too
    state.setCurrentName("Default");
    expect(get(state.configList).find((e) => e.current)?.name).toMatch(/Default\d+$/);
});

test("parent counts", () => {
    const state = startingState();

    expect(get(state.parentLimits)).toStrictEqual({ newCards: 10, reviews: 200 });

    // adjusting the current deck config won't alter parent
    state.currentConfig.update((c) => {
        c.newPerDay = 123;
        return c;
    });
    expect(get(state.parentLimits)).toStrictEqual({ newCards: 10, reviews: 200 });

    // but adjusting the default config will, since the parent deck uses it
    state.setCurrentIndex(0);
    state.currentConfig.update((c) => {
        c.newPerDay = 123;
        return c;
    });
    expect(get(state.parentLimits)).toStrictEqual({ newCards: 123, reviews: 200 });
});
