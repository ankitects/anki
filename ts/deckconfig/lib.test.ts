// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import * as pb from "anki/backend_proto";
import { DeckConfigState } from "./lib";

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

test("create", () => {
    const empty = pb.BackendProto.DeckConfigForUpdate.fromObject(exampleData);
    console.log(empty);
    const state = new DeckConfigState(empty);
    expect(state.currentDeck.name).toBe("Default::child");
});
