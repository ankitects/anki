// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { FsrsMemoryState } from "@generated/anki/cards_pb";
import { CardStatsResponse } from "@generated/anki/stats_pb";
import * as tr2 from "@generated/ftl";
import { expect, test } from "vitest";

import { rowsFromStats } from "./lib";

function baseStats(overrides?: Partial<CardStatsResponse>): CardStatsResponse {
    return new CardStatsResponse({
        cardId: 1n,
        noteId: 2n,
        deck: "Default",
        added: 1n,
        ease: 2500,
        reviews: 0,
        lapses: 0,
        averageSecs: 0,
        totalSecs: 0,
        cardType: "Card 1",
        notetype: "Basic",
        customData: "",
        preset: "Default",
        ...overrides,
    });
}

test("shows ease when fsrs is disabled", () => {
    const rows = rowsFromStats(baseStats({ fsrsEnabled: false }));

    expect(rows).toContainEqual({
        label: tr2.cardStatsEase(),
        value: "250%",
    });
    expect(rows.find((row) => row.label === tr2.cardStatsFsrsStability())).toBeUndefined();
    expect(rows.find((row) => row.label === tr2.cardStatsFsrsDifficulty())).toBeUndefined();
});

test("hides ease when fsrs is enabled", () => {
    const rows = rowsFromStats(baseStats({ fsrsEnabled: true }));

    expect(rows.find((row) => row.label === tr2.cardStatsEase())).toBeUndefined();
    expect(rows.find((row) => row.label === tr2.cardStatsFsrsStability())).toBeUndefined();
    expect(rows.find((row) => row.label === tr2.cardStatsFsrsDifficulty())).toBeUndefined();
});

test("with memoryState and fsrs enabled, shows FSRS rows and hides ease", () => {
    const rows = rowsFromStats(
        baseStats({
            fsrsEnabled: true,
            desiredRetention: 0.9,
            memoryState: new FsrsMemoryState({ stability: 20, difficulty: 7.3 }),
        }),
    );

    expect(rows.find((row) => row.label === tr2.cardStatsFsrsStability())).toBeDefined();
    expect(rows).toContainEqual({
        label: tr2.cardStatsFsrsDifficulty(),
        value: "70%",
    });
    expect(rows.find((row) => row.label === tr2.cardStatsEase())).toBeUndefined();
});
