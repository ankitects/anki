// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/* eslint
@typescript-eslint/no-explicit-any: "off",
 */

import { Notetypes } from "lib/proto";
import { ChangeNotetypeState, negativeOneToNull, MapContext } from "./lib";
import { get } from "svelte/store";

const exampleNames = {
    entries: [
        {
            id: "1623289129847",
            name: "Basic",
        },
        {
            id: "1623289129848",
            name: "Basic (and reversed card)",
        },
        {
            id: "1623289129849",
            name: "Basic (optional reversed card)",
        },
        {
            id: "1623289129850",
            name: "Basic (type in the answer)",
        },
        {
            id: "1623289129851",
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
        oldNotetypeId: "1623289129847",
        newNotetypeId: "1623289129849",
        currentSchema: "1623302002316",
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
        oldNotetypeId: "1623289129847",
        newNotetypeId: "1623289129847",
        currentSchema: "1623302002316",
    },
};

function differentState(): ChangeNotetypeState {
    return new ChangeNotetypeState(
        Notetypes.NotetypeNames.fromObject(exampleNames),
        Notetypes.ChangeNotetypeInfo.fromObject(exampleInfoDifferent)
    );
}

function sameState(): ChangeNotetypeState {
    return new ChangeNotetypeState(
        Notetypes.NotetypeNames.fromObject(exampleNames),
        Notetypes.ChangeNotetypeInfo.fromObject(exampleInfoSame)
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
        ["Front", "Back", "(Nothing)"]
    );
    expect(get(state.info).getOldIndex(MapContext.Field, 0)).toBe(0);
    expect(get(state.info).getOldIndex(MapContext.Field, 1)).toBe(1);
    expect(get(state.info).getOldIndex(MapContext.Field, 2)).toBe(2);
    state.setOldIndex(MapContext.Field, 2, 0);
    expect(get(state.info).getOldIndex(MapContext.Field, 2)).toBe(0);

    // the same template shouldn't be mappable twice
    expect(
        get(state.info).getOldNamesIncludingNothing(MapContext.Template)
    ).toStrictEqual(["Card 1", "(Nothing)"]);
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
