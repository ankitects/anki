// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/* eslint
@typescript-eslint/no-explicit-any: "off",
 */

import { protoBase64 } from "@bufbuild/protobuf";
import {
    DeckConfig_Config_LeechAction,
    DeckConfigsForUpdate,
    UpdateDeckConfigsMode,
} from "@generated/anki/deck_config_pb";
import { get } from "svelte/store";
import { expect, test } from "vitest";

import { DeckOptionsState } from "./lib";

const exampleData = {
    allConfig: [
        {
            config: {
                id: 1n,
                name: "Default",
                mtimeSecs: 1618570764n,
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
                    leechAction: DeckConfig_Config_LeechAction.TAG_ONLY,
                    leechThreshold: 8,
                    capAnswerTimeToSecs: 60,
                    other: protoBase64.dec(
                        "eyJuZXciOnsic2VwYXJhdGUiOnRydWV9LCJyZXYiOnsiZnV6eiI6MC4wNSwibWluU3BhY2UiOjF9fQ==",
                    ),
                },
            },
            useCount: 1,
        },
        {
            config: {
                id: 1618570764780n,
                name: "another one",
                mtimeSecs: 1618570781n,
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
                    leechAction: DeckConfig_Config_LeechAction.TAG_ONLY,
                    leechThreshold: 8,
                    capAnswerTimeToSecs: 60,
                },
            },
            useCount: 1,
        },
    ],
    currentDeck: {
        name: "Default::child",
        configId: 1618570764780n,
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
            leechAction: DeckConfig_Config_LeechAction.TAG_ONLY,
            leechThreshold: 8,
            capAnswerTimeToSecs: 60,
        },
    },
};

function startingState(): DeckOptionsState {
    return new DeckOptionsState(
        123n,
        new DeckConfigsForUpdate(exampleData),
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
            idx: 0,
            name: "another one",
            useCount: 1,
        },
        {
            current: false,
            idx: 1,
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
            idx: 1,
            name: "hello",
            useCount: 1,
        },
        {
            current: false,
            idx: 2,
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
            idx: 1,
            name: "hello",
            useCount: 0,
        },
        {
            current: false,
            idx: 2,
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
    const out = state.dataForSaving(UpdateDeckConfigsMode.NORMAL);
    expect(out.removedConfigIds).toStrictEqual([1618570764780n]);
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

test("saving", () => {
    let state = startingState();
    let out = state.dataForSaving(UpdateDeckConfigsMode.NORMAL);
    expect(out.removedConfigIds).toStrictEqual([]);
    expect(out.targetDeckId).toBe(123n);
    // in no-changes case, currently selected config should
    // be returned
    expect(out.configs!.length).toBe(1);
    expect(out.configs![0].name).toBe("another one");
    expect(out.mode).toBe(UpdateDeckConfigsMode.NORMAL);

    // rename, then change current deck
    state.setCurrentName("zzz");
    out = state.dataForSaving(UpdateDeckConfigsMode.APPLY_TO_CHILDREN);
    state.setCurrentIndex(0);

    // renamed deck should be in changes, with current deck as last element
    out = state.dataForSaving(UpdateDeckConfigsMode.APPLY_TO_CHILDREN);
    expect(out.configs!.map((c) => c.name)).toStrictEqual(["zzz", "Default"]);
    expect(out.mode).toBe(UpdateDeckConfigsMode.APPLY_TO_CHILDREN);

    // start again, adding new deck
    state = startingState();
    state.addConfig("hello");

    // deleting it should not change removedConfigs
    state.removeCurrentConfig();
    out = state.dataForSaving(UpdateDeckConfigsMode.APPLY_TO_CHILDREN);
    expect(out.removedConfigIds).toStrictEqual([]);

    // select the other non-default deck & remove
    state.setCurrentIndex(0);
    state.removeCurrentConfig();

    // should be listed in removedConfigs, and modified should
    // only contain Default, which is the new current deck
    out = state.dataForSaving(UpdateDeckConfigsMode.APPLY_TO_CHILDREN);
    expect(out.removedConfigIds).toStrictEqual([1618570764780n]);
    expect(out.configs!.map((c) => c.name)).toStrictEqual(["Default"]);
});

test("aux data", () => {
    const state = startingState();
    expect(get(state.currentAuxData)).toStrictEqual({});
    state.currentAuxData.update((val) => {
        return { ...val, hello: "world" };
    });

    // check default
    state.setCurrentIndex(1);
    expect(get(state.currentAuxData)).toStrictEqual({
        new: {
            separate: true,
        },
        rev: {
            fuzz: 0.05,
            minSpace: 1,
        },
    });
    state.currentAuxData.update((val) => {
        return { ...val, defaultAddition: true };
    });

    // ensure changes serialize
    const out = state.dataForSaving(UpdateDeckConfigsMode.APPLY_TO_CHILDREN);
    expect(out.configs!.length).toBe(2);
    const json = out.configs!.map((c) => JSON.parse(new TextDecoder().decode(c.config!.other)));
    expect(json).toStrictEqual([
        // other deck comes first
        {
            hello: "world",
        },
        // default is selected, so comes last
        {
            defaultAddition: true,
            new: {
                separate: true,
            },
            rev: {
                fuzz: 0.05,
                minSpace: 1,
            },
        },
    ]);
});
