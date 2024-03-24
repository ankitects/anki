// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/* eslint
@typescript-eslint/no-explicit-any: "off",
 */

import "@tslib/i18n";

import { ChangeNotetypeInfo, NotetypeNames } from "@generated/anki/notetypes_pb";
import * as tr from "@generated/ftl";
import { get } from "svelte/store";
import { expect, test } from "vitest";

import { ChangeNotetypeState, MapContext, negativeOneToNull } from "./lib";

const exampleNames = {
    entries: [
        {
            id: 1623289129847n,
            name: "Basic",
        },
        {
            id: 1623289129848n,
            name: "Basic (and reversed card)",
        },
        {
            id: 1623289129849n,
            name: "Basic (optional reversed card)",
        },
        {
            id: 1623289129850n,
            name: "Basic (type in the answer)",
        },
        {
            id: 1623289129851n,
            name: "Cloze",
        },
    ],
};

const exampleInfoDifferent = {
    oldFieldNames: ["Front", "Back"],
    oldTemplateNames: ["Card 1"],
    newFieldNames: ["Front", "Back", "Add Reverse"],
    newTemplateNames: ["Card 1", "Card 2"],
    input: {
        newFields: [0, 1, -1],
        newTemplates: [0, -1],
        oldNotetypeId: 1623289129847n,
        newNotetypeId: 1623289129849n,
        currentSchema: 1623302002316n,
        oldNotetypeName: "Basic",
    },
};

const exampleInfoSame = {
    oldFieldNames: ["Front", "Back"],
    oldTemplateNames: ["Card 1"],
    newFieldNames: ["Front", "Back"],
    newTemplateNames: ["Card 1"],
    input: {
        newFields: [0, 1],
        newTemplates: [0],
        oldNotetypeId: 1623289129847n,
        newNotetypeId: 1623289129847n,
        currentSchema: 1623302002316n,
        oldNotetypeName: "Basic",
    },
};

function differentState(): ChangeNotetypeState {
    return new ChangeNotetypeState(
        new NotetypeNames(exampleNames),
        new ChangeNotetypeInfo(exampleInfoDifferent),
    );
}

function sameState(): ChangeNotetypeState {
    return new ChangeNotetypeState(
        new NotetypeNames(exampleNames),
        new ChangeNotetypeInfo(exampleInfoSame),
    );
}

test("proto conversion", () => {
    const state = differentState();
    expect(get(state.info).fields).toStrictEqual([0, 1, null]);
    expect(negativeOneToNull(state.dataForSaving().newFields)).toStrictEqual([
        0,
        1,
        null,
    ]);
});

test("mapping", () => {
    const state = differentState();
    expect(get(state.info).getNewName(MapContext.Field, 0)).toBe("Front");
    expect(get(state.info).getNewName(MapContext.Field, 1)).toBe("Back");
    expect(get(state.info).getNewName(MapContext.Field, 2)).toBe("Add Reverse");
    expect(get(state.info).getOldNamesIncludingNothing(MapContext.Field)).toStrictEqual(
        ["Front", "Back", tr.changeNotetypeNothing()],
    );
    expect(get(state.info).getOldIndex(MapContext.Field, 0)).toBe(0);
    expect(get(state.info).getOldIndex(MapContext.Field, 1)).toBe(1);
    expect(get(state.info).getOldIndex(MapContext.Field, 2)).toBe(2);
    state.setOldIndex(MapContext.Field, 2, 0);
    expect(get(state.info).getOldIndex(MapContext.Field, 2)).toBe(0);

    // the same template shouldn't be mappable twice
    expect(
        get(state.info).getOldNamesIncludingNothing(MapContext.Template),
    ).toStrictEqual(["Card 1", tr.changeNotetypeNothing()]);
    expect(get(state.info).getOldIndex(MapContext.Template, 0)).toBe(0);
    expect(get(state.info).getOldIndex(MapContext.Template, 1)).toBe(1);
    state.setOldIndex(MapContext.Template, 1, 0);
    expect(get(state.info).getOldIndex(MapContext.Template, 0)).toBe(1);
    expect(get(state.info).getOldIndex(MapContext.Template, 1)).toBe(0);
});

test("unused", () => {
    const state = differentState();
    expect(get(state.info).unusedItems(MapContext.Field)).toStrictEqual([]);
    state.setOldIndex(MapContext.Field, 0, 2);
    expect(get(state.info).unusedItems(MapContext.Field)).toStrictEqual(["Front"]);
});

test("unchanged", () => {
    let state = differentState();
    expect(get(state.info).unchanged()).toBe(false);
    state = sameState();
    expect(get(state.info).unchanged()).toBe(true);
});
